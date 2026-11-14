package busKaroo.dao;

import busKaroo.model.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByUserIdAndCouponCodeIgnoreCase(Long userId, String couponCode);
}
