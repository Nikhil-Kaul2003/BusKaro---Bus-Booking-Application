package busKaroo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Permanent ledger written by PostgreSQL when a reservation is confirmed. */
@Entity
@Table(name = "coupon_redemption")
public class CouponRedemption extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_code", nullable = false, length = 32)
    private String couponCode;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "redeemed_at", nullable = false)
    private OffsetDateTime redeemedAt;

    public Long getUserId() { return userId; }
    public String getCouponCode() { return couponCode; }
    public Long getReservationId() { return reservationId; }
    public OffsetDateTime getRedeemedAt() { return redeemedAt; }
}

