package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.response.FavoriteResponse;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@Tag(name = "Favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    public FavoriteController(FavoriteService favoriteService) { this.favoriteService = favoriteService; }

    @GetMapping
    @Operation(summary = "Get my favorite restaurants")
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> getMyFavorites(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getMyFavorites(userDetails.getUsername())));
    }

    @PostMapping("/{restaurantId}")
    @Operation(summary = "Add restaurant to favorites")
    public ResponseEntity<ApiResponse<FavoriteResponse>> add(
            @PathVariable UUID restaurantId, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Added to favorites", favoriteService.add(restaurantId, userDetails.getUsername())));
    }

    @DeleteMapping("/{restaurantId}")
    @Operation(summary = "Remove restaurant from favorites")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable UUID restaurantId, @AuthenticationPrincipal UserDetails userDetails) {
        favoriteService.remove(restaurantId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Removed from favorites", null));
    }
}
