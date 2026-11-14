package busKaroo.dao;

import busKaroo.model.Reservation;
import busKaroo.model.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    // Existing queries - preserved.
    List<Reservation> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = {
            "trip.route", "trip.bus", "trip.driver.user",
            "originRouteStop.terminal", "targetRouteStop.terminal", "passengers"
    })
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query(value = "UPDATE reservation SET status = 'EXPIRED', updated_at = NOW() " +
            "WHERE status = 'PENDING' " +
            "AND created_at < NOW() - (:ttlMinutes * INTERVAL '1 minute')", nativeQuery = true)
    int expirePendingReservations(@Param("ttlMinutes") int ttlMinutes);

    // Required by the original coupon integration for FIRST25 / BACK10.
    boolean existsByUserIdAndStatus(Long userId, ReservationStatus status);

    // Lock the reservation record while the payment transaction completes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForPayment(@Param("id") Long id);
}
