package busKaroo.dto.response;

import java.math.BigDecimal;

public record CouponQuoteResponse(
        String couponCode,
        BigDecimal eligibleFare,
        BigDecimal bookingFee,
        BigDecimal originalTotal,
        BigDecimal discountAmount,
        BigDecimal finalTotal) {}
