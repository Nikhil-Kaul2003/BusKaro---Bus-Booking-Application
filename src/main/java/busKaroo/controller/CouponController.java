package busKaroo.controller;

import busKaroo.dto.response.CouponOfferResponse;
import busKaroo.security.SecurityUtils;
import busKaroo.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService couponService;
    private final SecurityUtils securityUtils;

    public CouponController(CouponService couponService, SecurityUtils securityUtils) {
        this.couponService = couponService;
        this.securityUtils = securityUtils;
    }

    /** Public catalog; authenticated per-user usage is available via /my. */
    @GetMapping
    public ResponseEntity<List<CouponOfferResponse>> list() {
        return ResponseEntity.ok(couponService.listOffers(null));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CouponOfferResponse>> myOffers() {
        return ResponseEntity.ok(couponService.listOffers(securityUtils.getCurrentUserId()));
    }
}
