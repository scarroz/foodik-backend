package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.CreateReservationRequest;
import co.edu.unbosque.foodik.domain.dto.response.ReservationResponse;
import co.edu.unbosque.foodik.domain.dto.response.TableSlotResponse;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;
    public ReservationController(ReservationService reservationService) { this.reservationService = reservationService; }

    @GetMapping("/restaurants/{restaurantId}/availability")
    @Operation(summary = "Get available table slots")
    public ResponseEntity<ApiResponse<List<TableSlotResponse>>> getAvailability(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "2") int partySize) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.getAvailability(restaurantId, date, partySize)));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Create a reservation")
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Reservation created",
                reservationService.create(request, userDetails.getUsername())));
    }

    @GetMapping("/reservations/my")
    @Operation(summary = "Get my reservations")
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> getMyReservations(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.getMyReservations(userDetails.getUsername(), pageable)));
    }

    @GetMapping("/reservations/{id}")
    @Operation(summary = "Get reservation by ID")
    public ResponseEntity<ApiResponse<ReservationResponse>> findById(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.findById(id, userDetails.getUsername())));
    }

    @PutMapping("/reservations/{id}/cancel")
    @Operation(summary = "Cancel a reservation")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok("Reservation cancelled", reservationService.cancel(id, userDetails.getUsername())));
    }

    @GetMapping("/restaurants/{restaurantId}/reservations")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Get all reservations for a restaurant (admin)")
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> getRestaurantReservations(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                reservationService.getRestaurantReservations(restaurantId, userDetails.getUsername(), pageable)));
    }

    @PutMapping("/reservations/{id}/status")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Update reservation status (admin)")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateStatus(
            @PathVariable UUID id, @RequestParam ReservationStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                reservationService.updateStatus(id, status, userDetails.getUsername())));
    }
}
