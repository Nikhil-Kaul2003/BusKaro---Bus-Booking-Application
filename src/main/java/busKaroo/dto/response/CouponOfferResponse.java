package busKaroo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponOfferResponse(
        String code,
        String title,
        String eligibilityType,
        BigDecimal percentOff,
        BigDecimal maxDiscount,
        BigDecimal minFare,
        LocalDate validFrom,
        LocalDate validUntil,
        LocalDate travelFrom,
        LocalDate travelUntil,
        boolean alreadyUsed) {}
