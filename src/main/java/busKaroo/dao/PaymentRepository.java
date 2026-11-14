package busKaroo.dao;

import busKaroo.model.Payment;
import busKaroo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Existing queries - preserved.
    List<Payment> findByUserId(Long userId);
    List<Payment> findByReservationId(Long reservationId);

    // Has this reservation already been paid? Defends against inconsistent state.
    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    // Historical success, even if the paid reservation was later cancelled.
    boolean existsByUserIdAndStatus(Long userId, PaymentStatus status);
}
