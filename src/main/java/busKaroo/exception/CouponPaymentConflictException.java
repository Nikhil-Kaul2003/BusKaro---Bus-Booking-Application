package busKaroo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CouponPaymentConflictException extends RuntimeException {
    public CouponPaymentConflictException(String message) {
        super(message);
    }
}
