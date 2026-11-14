package busKaroo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReservationResponse(
        Long id,
        String bookingReference,
        String status,
        BigDecimal totalAmount,
        LocalDate createdAt,
        TripSummary trip,
        String originTerminalName,
        String targetTerminalName,
        List<PassengerResponse> passengers,
        BigDecimal distance,
        String couponCode,
        BigDecimal originalAmount,
        BigDecimal discountAmount) {

    // Allows your current ReservationMapper (old 10-argument constructor) to keep compiling.
    public ReservationResponse(Long id, String bookingReference, String status,
            BigDecimal totalAmount, LocalDate createdAt, TripSummary trip,
            String originTerminalName, String targetTerminalName,
            List<PassengerResponse> passengers, BigDecimal distance) {
        this(id, bookingReference, status, totalAmount, createdAt, trip,
                originTerminalName, targetTerminalName, passengers, distance,
                null, null, BigDecimal.ZERO);
    }

    public record TripSummary(Long id, String routeName, OffsetDateTime departure,
            OffsetDateTime arrivalDatetime, String driverName, String busName,
            String busRegistrationNumber, String seatLayout) {}

    public record PassengerResponse(String firstName, String lastName,
            String seatNumber, boolean checkedIn) {}
}
