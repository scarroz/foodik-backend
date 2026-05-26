package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.response.RestaurantScrapedInfoResponse;
import java.util.List;
public interface ScrapingService {
    RestaurantScrapedInfoResponse getRestaurantInfo(String name, String city);
    List<RestaurantScrapedInfoResponse> getNearbyInfo(double lat, double lng, int radiusM);
}
