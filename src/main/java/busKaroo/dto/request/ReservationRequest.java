package busKaroo.dto.request;

import java.util.List;

public record ReservationRequest(Long userId, Long tripId, int fromStopOrder,
        int toStopOrder, List<PassengerSeatRequest> passengers, String couponCode) {
    public ReservationRequest(Long userId, Long tripId, int fromStopOrder,
            int toStopOrder, List<PassengerSeatRequest> passengers) {
        this(userId, tripId, fromStopOrder, toStopOrder, passengers, null);
    }
}
