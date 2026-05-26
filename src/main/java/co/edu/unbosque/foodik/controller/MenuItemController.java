package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.MenuItemResponse;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.MenuItemService;
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
@Tag(name = "Menu")
public class MenuItemController {

    private final MenuItemService menuItemService;
    public MenuItemController(MenuItemService menuItemService) { this.menuItemService = menuItemService; }

    @GetMapping("/api/v1/restaurants/{restaurantId}/menu")
    @Operation(summary = "Get menu for a restaurant")
    public ResponseEntity<ApiResponse<List<MenuItemResponse>>> getMenu(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(menuItemService.findByRestaurant(restaurantId)));
    }

    @PostMapping("/api/v1/restaurants/{restaurantId}/menu")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Add menu item")
    public ResponseEntity<ApiResponse<MenuItemResponse>> create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Menu item created",
                menuItemService.create(restaurantId, request, userDetails.getUsername())));
    }

    @PutMapping("/api/v1/menu-items/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Update menu item")
    public ResponseEntity<ApiResponse<MenuItemResponse>> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Menu item updated",
                menuItemService.update(id, request, userDetails.getUsername())));
    }

    @DeleteMapping("/api/v1/menu-items/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Remove menu item")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        menuItemService.delete(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Menu item removed", null));
    }
}
