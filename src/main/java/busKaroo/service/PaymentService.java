package busKaroo.service;

import busKaroo.dao.CouponRedemptionRepository;
import busKaroo.dao.PaymentRepository;
import busKaroo.dao.ReservationRepository;
import busKaroo.exception.CouponPaymentConflictException;
import busKaroo.exception.ForbiddenException;
import busKaroo.exception.ReservationNotFoundException;
import busKaroo.model.Payment;
import busKaroo.model.Reservation;
import busKaroo.model.User;
import busKaroo.model.enums.PaymentStatus;
import busKaroo.model.enums.ReservationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final ReservationRepository reservationRepo;
    private final CouponRedemptionRepository couponRedemptionRepo;
    private final EntityManager entityManager;

    @Value("${app.reservations.pending-ttl-minutes:15}")
    private int pendingTtlMinutes = 15;

    @Autowired
    public PaymentService(PaymentRepository paymentRepo,
                          ReservationRepository reservationRepo,
                          CouponRedemptionRepository couponRedemptionRepo,
                          EntityManager entityManager) {
        this.paymentRepo = paymentRepo;
        this.reservationRepo = reservationRepo;
        this.couponRedemptionRepo = couponRedemptionRepo;
        this.entityManager = entityManager;
    }

    // Keeps older tests that directly call the existing two-argument constructor compiling.
    // Runtime Spring wiring uses the @Autowired constructor above, with transactional locks.
    public PaymentService(PaymentRepository paymentRepo, ReservationRepository reservationRepo) {
        this(paymentRepo, reservationRepo, null, null);
    }

    @Transactional
    public boolean payReservation(Long reservationId, Long userId) {
        // Serialize payments by the same user, including separate PENDING reservations
        // with the same coupon. Combined with PostgreSQL's redemption UNIQUE constraint,
        // this prevents duplicate redemptions under concurrent requests.
        if (entityManager != null) {
            User account = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
            if (account == null) {
                throw new ForbiddenException("User account was not found");
            }
        }

        Reservation reservation = entityManager == null
                ? reservationRepo.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId))
                : reservationRepo.findByIdForPayment(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only pay for your own reservations");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new CouponPaymentConflictException(
                    "Reservation is not pending. Current status: " + reservation.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (reservation.getCreatedAt() != null
                && reservation.getCreatedAt().isBefore(now.minusMinutes(pendingTtlMinutes))) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepo.save(reservation);
            return false;
        }

        // Do not create another COMPLETED payment if records have become inconsistent.
        if (paymentRepo.existsByReservationIdAndStatus(reservationId, PaymentStatus.COMPLETED)) {
            throw new CouponPaymentConflictException("This reservation already has a completed payment");
        }

        String couponCode = reservation.getCouponCode();
        if (couponCode != null && !couponCode.isBlank()) {
            if (couponRedemptionRepo == null) {
                throw new IllegalStateException("CouponRedemptionRepository is required for coupon payments");
            }
            if (couponRedemptionRepo.existsByUserIdAndCouponCodeIgnoreCase(userId, couponCode)) {
                throw new CouponPaymentConflictException("You have already used this coupon");
            }

            boolean hasPreviousCompletedPayment =
                    paymentRepo.existsByUserIdAndStatus(userId, PaymentStatus.COMPLETED)
                    || reservationRepo.existsByUserIdAndStatus(userId, ReservationStatus.CONFIRMED);

            // A cancelled paid booking still counts as a previous journey.
            if ("FIRST25".equalsIgnoreCase(couponCode) && hasPreviousCompletedPayment) {
                throw new CouponPaymentConflictException(
                        "FIRST25 is only available before your first successful payment");
            }
            if ("BACK10".equalsIgnoreCase(couponCode) && !hasPreviousCompletedPayment) {
                throw new CouponPaymentConflictException(
                        "BACK10 requires a previous successfully paid booking");
            }
        }

        // Keep your current mock payment gateway behavior.
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setUser(reservation.getUser());
        payment.setAmount(reservation.getTotalAmount()); // Final discounted amount.
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setPaidAt(now);
        payment.setGatewayResponse("{\"status\":\"success\",\"mock\":true}");
        paymentRepo.save(payment);

        // The existing PostgreSQL trigger inserts coupon_redemption when status
        // changes to CONFIRMED. The payment and status update are atomic.
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepo.saveAndFlush(reservation);
        return true;
    }
}
