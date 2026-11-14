package busKaroo.service;

import busKaroo.dao.CouponRedemptionRepository;
import busKaroo.dao.CouponRepository;
import busKaroo.dao.PaymentRepository;
import busKaroo.dao.ReservationRepository;
import busKaroo.dto.response.CouponOfferResponse;
import busKaroo.dto.response.CouponQuoteResponse;
import busKaroo.exception.CouponNotEligibleException;
import busKaroo.model.Coupon;
import busKaroo.model.Trip;
import busKaroo.model.enums.ReservationStatus;
import busKaroo.model.enums.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class CouponService {
    private static final BigDecimal BOOKING_FEE = new BigDecimal("20.00");
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;

    @Autowired
    public CouponService(CouponRepository coupons,
                         CouponRedemptionRepository redemptions,
                         ReservationRepository reservations,
                         PaymentRepository payments) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.reservations = reservations;
        this.payments = payments;
    }

    // For older unit tests that construct this service manually.
    public CouponService(CouponRepository coupons,
                         CouponRedemptionRepository redemptions,
                         ReservationRepository reservations) {
        this(coupons, redemptions, reservations, null);
    }

    @Transactional(readOnly = true)
    public List<CouponOfferResponse> listOffers(Long userId) {
        return coupons.findByActiveTrueOrderByIdAsc().stream()
                .map(c -> new CouponOfferResponse(c.getCode(), c.getTitle(), c.getEligibilityType(),
                        c.getPercentOff(), c.getMaxDiscount(), c.getMinFare(),
                        c.getValidFrom(), c.getValidUntil(), c.getTravelFrom(), c.getTravelUntil(),
                        userId != null && redemptions.existsByUserIdAndCouponCodeIgnoreCase(userId, c.getCode())))
                .toList();
    }

    /** Prices are always calculated server-side. Percentage does not discount the booking fee. */
    @Transactional(readOnly = true)
    public CouponQuoteResponse quote(Long userId, Trip trip, BigDecimal fareBeforeFee, String rawCouponCode) {
        BigDecimal eligibleFare = fareBeforeFee.setScale(2, RoundingMode.HALF_UP);
        if (eligibleFare.signum() < 0) {
            throw new IllegalArgumentException("Fare cannot be negative");
        }
        BigDecimal originalTotal = eligibleFare.add(BOOKING_FEE);
        if (rawCouponCode == null || rawCouponCode.isBlank()) {
            return new CouponQuoteResponse(null, eligibleFare, BOOKING_FEE,
                    originalTotal, BigDecimal.ZERO.setScale(2), originalTotal);
        }

        String code = rawCouponCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() > 32) {
            throw new CouponNotEligibleException("Coupon code is too long");
        }
        Coupon coupon = coupons.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new CouponNotEligibleException("Coupon code was not found"));
        if (!coupon.isActive()) {
            throw new CouponNotEligibleException("This coupon is not currently active");
        }

        LocalDate today = LocalDate.now(INDIA);
        LocalDate travelDay = trip.getDepartureDatetime().atZoneSameInstant(INDIA).toLocalDate();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())
                || coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            throw new CouponNotEligibleException("This coupon is outside its booking period");
        }
        if (coupon.getTravelFrom() != null && travelDay.isBefore(coupon.getTravelFrom())
                || coupon.getTravelUntil() != null && travelDay.isAfter(coupon.getTravelUntil())) {
            throw new CouponNotEligibleException("This coupon is not valid for the selected travel date");
        }
        if (eligibleFare.compareTo(coupon.getMinFare()) < 0) {
            throw new CouponNotEligibleException("Minimum ticket fare for " + code + " is ₹" + coupon.getMinFare());
        }
        if (redemptions.existsByUserIdAndCouponCodeIgnoreCase(userId, code)) {
            throw new CouponNotEligibleException("You have already used this coupon");
        }

        // A paid trip remains part of a customer's history even after cancellation.
        // Retain the CONFIRMED check for legacy reservations with no payment row.
        boolean hasConfirmed = (payments != null
                && payments.existsByUserIdAndStatus(userId, PaymentStatus.COMPLETED))
                || reservations.existsByUserIdAndStatus(userId, ReservationStatus.CONFIRMED);
        switch (coupon.getEligibilityType()) {
            case "FIRST_BOOKING" -> {
                if (hasConfirmed) throw new CouponNotEligibleException("Only valid for your first confirmed booking");
            }
            case "RETURNING" -> {
                if (!hasConfirmed) throw new CouponNotEligibleException("A previous confirmed booking is required");
            }
            case "WEEKEND" -> {
                if (travelDay.getDayOfWeek() != DayOfWeek.SATURDAY
                        && travelDay.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    throw new CouponNotEligibleException("This offer requires Saturday or Sunday travel");
                }
            }
            case "SLEEPER" -> {
                if (trip.getBus().getCategory() == null
                        || !"Sleeper".equalsIgnoreCase(trip.getBus().getCategory().getName())) {
                    throw new CouponNotEligibleException("This offer is for Sleeper-category buses only");
                }
            }
            case "FESTIVAL" -> {
                if (coupon.getValidFrom() == null || coupon.getValidUntil() == null
                        || coupon.getTravelFrom() == null || coupon.getTravelUntil() == null) {
                    throw new CouponNotEligibleException("Festival dates have not been configured");
                }
            }
            case "MONTHLY" -> {
                if (coupon.getValidFrom() == null || coupon.getValidUntil() == null) {
                    throw new CouponNotEligibleException("Monthly offer dates have not been configured");
                }
            }
            default -> throw new CouponNotEligibleException("Unsupported coupon eligibility type");
        }

        BigDecimal discount = eligibleFare.multiply(coupon.getPercentOff())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                .min(coupon.getMaxDiscount()).min(eligibleFare);
        return new CouponQuoteResponse(code, eligibleFare, BOOKING_FEE, originalTotal,
                discount, originalTotal.subtract(discount));
    }
}
