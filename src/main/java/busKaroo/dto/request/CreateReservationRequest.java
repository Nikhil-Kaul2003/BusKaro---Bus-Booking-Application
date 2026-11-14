package busKaroo.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateReservationRequest(
        @NotNull Long tripId,
        @NotNull Integer fromStopOrder,
        @NotNull Integer toStopOrder,
        @NotEmpty List<PassengerSeatRequest> passengers,
        @Size(max = 32) String couponCode
) {
    // Retain compatibility with existing Java callers constructing the old 4-field record.
    public CreateReservationRequest(Long tripId, Integer fromStopOrder,
            Integer toStopOrder, List<PassengerSeatRequest> passengers) {
        this(tripId, fromStopOrder, toStopOrder, passengers, null);
    }
}
