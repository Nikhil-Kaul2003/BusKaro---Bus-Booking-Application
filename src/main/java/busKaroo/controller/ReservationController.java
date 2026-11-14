package busKaroo.controller;

import busKaroo.dto.mapper.ReservationMapper;
import busKaroo.dto.request.CreateReservationRequest;
import busKaroo.dto.response.CouponQuoteResponse;
import busKaroo.dto.response.ReservationResponse;
import busKaroo.exception.ForbiddenException;
import busKaroo.exception.ApiError;
import busKaroo.model.Reservation;
import busKaroo.security.SecurityUtils;
import busKaroo.service.PaymentService;
import busKaroo.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final SecurityUtils securityUtils;
    private final ReservationMapper reservationMapper;

    public ReservationController(ReservationService reservationService,
            PaymentService paymentService, SecurityUtils securityUtils,
            ReservationMapper reservationMapper) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.securityUtils = securityUtils;
        this.reservationMapper = reservationMapper;
    }

    @PostMapping("/quote")
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<CouponQuoteResponse> quote(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(reservationService.previewReservation(securityUtils.getCurrentUserId(), request));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request) {
        Long currentUserId = securityUtils.getCurrentUserId();
        Reservation reservation = reservationService.createReservation(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReservationResponse>> getMyReservations() {
        Long currentUserId = securityUtils.getCurrentUserId();
        List<Reservation> reservations = reservationService.findByUserId(currentUserId);
        return ResponseEntity.ok(reservations.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable Long id) {
        Long currentUserId = securityUtils.getCurrentUserId();
        Reservation reservation = reservationService.findById(id);
        if (!reservation.getUser().getId().equals(currentUserId)
                && !securityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("Access denied");
        }
        return ResponseEntity.ok(toResponse(reservation));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> payReservation(@PathVariable Long id) {
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!paymentService.payReservation(id, currentUserId)) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(new ApiError("Reservation expired", "The payment hold expired. Please book the trip again."));
        }
        return ResponseEntity.ok().build();
    }

    /** Preserve your existing ReservationMapper without requiring its source file. */
    private ReservationResponse toResponse(Reservation reservation) {
        ReservationResponse original = reservationMapper.toResponse(reservation);
        return new ReservationResponse(
                original.id(), original.bookingReference(), original.status(),
                original.totalAmount(), original.createdAt(), original.trip(),
                original.originTerminalName(), original.targetTerminalName(),
                original.passengers(), original.distance(),
                reservation.getCouponCode(), reservation.getOriginalAmount(),
                reservation.getDiscountAmount());
    }
}
