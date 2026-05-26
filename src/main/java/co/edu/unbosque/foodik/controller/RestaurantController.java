package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.RestaurantResponse;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;
    public RestaurantController(RestaurantService restaurantService) { this.restaurantService = restaurantService; }

    @GetMapping
    @Operation(summary = "Find nearby restaurants")
    public ResponseEntity<ApiResponse<Page<RestaurantResponse>>> findNearby(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius,
            @RequestParam(required = false) RestaurantCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.findNearby(lat, lng, radius, category, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant by ID")
    public ResponseEntity<ApiResponse<RestaurantResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Create a restaurant")
    public ResponseEntity<ApiResponse<RestaurantResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Restaurant created", restaurantService.create(request, userDetails.getUsername())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Update restaurant")
    public ResponseEntity<ApiResponse<RestaurantResponse>> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateRestaurantRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Restaurant updated", restaurantService.update(id, request, userDetails.getUsername())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Deactivate restaurant")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        restaurantService.delete(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Restaurant deactivated", null));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my restaurants")
    public ResponseEntity<ApiResponse<List<RestaurantResponse>>> getMyRestaurants(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.getByOwner(userDetails.getUsername())));
    }
}
