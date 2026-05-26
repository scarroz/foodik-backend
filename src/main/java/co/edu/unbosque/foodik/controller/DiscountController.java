package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.CreateDiscountRequest;
import co.edu.unbosque.foodik.domain.dto.response.DiscountResponse;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.DiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Discounts")
public class DiscountController {

    private final DiscountService discountService;
    public DiscountController(DiscountService discountService) { this.discountService = discountService; }

    @GetMapping("/api/v1/restaurants/{restaurantId}/discounts")
    @Operation(summary = "Get active discounts for a restaurant")
    public ResponseEntity<ApiResponse<List<DiscountResponse>>> getDiscounts(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(discountService.findActiveByRestaurant(restaurantId)));
    }

    @PostMapping("/api/v1/restaurants/{restaurantId}/discounts")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Create a discount")
    public ResponseEntity<ApiResponse<DiscountResponse>> create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateDiscountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Discount created",
                discountService.create(restaurantId, request, userDetails.getUsername())));
    }

    @DeleteMapping("/api/v1/discounts/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Deactivate a discount")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        discountService.delete(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Discount deactivated", null));
    }
}
