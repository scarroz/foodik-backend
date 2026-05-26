package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.response.RestaurantScrapedInfoResponse;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.ScrapingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scraping")
@Tag(name = "Scraping", description = "Enriched restaurant info from public sources")
public class ScrapingController {

    private final ScrapingService scrapingService;
    public ScrapingController(ScrapingService scrapingService) { this.scrapingService = scrapingService; }

    @GetMapping("/restaurant")
    @Operation(summary = "Get enriched info for a restaurant (price range, description, rating)")
    public ResponseEntity<ApiResponse<RestaurantScrapedInfoResponse>> getRestaurantInfo(
            @RequestParam String name, @RequestParam String city) {
        return ResponseEntity.ok(ApiResponse.ok(scrapingService.getRestaurantInfo(name, city)));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Scrape nearby restaurants from public sources")
    public ResponseEntity<ApiResponse<List<RestaurantScrapedInfoResponse>>> getNearby(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radiusM) {
        return ResponseEntity.ok(ApiResponse.ok(scrapingService.getNearbyInfo(lat, lng, radiusM)));
    }
}
