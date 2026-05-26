package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.response.RestaurantScrapedInfoResponse;
import co.edu.unbosque.foodik.integration.RestaurantScrapingClient;
import co.edu.unbosque.foodik.service.ScrapingService;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class ScrapingServiceImpl implements ScrapingService {
    private final RestaurantScrapingClient scrapingClient;
    public ScrapingServiceImpl(RestaurantScrapingClient scrapingClient) { this.scrapingClient = scrapingClient; }
    @Override
    public RestaurantScrapedInfoResponse getRestaurantInfo(String name, String city) {
        return scrapingClient.scrapeRestaurantInfo(name, city);
    }
    @Override
    public List<RestaurantScrapedInfoResponse> getNearbyInfo(double lat, double lng, int radiusM) {
        return scrapingClient.scrapeNearby(lat, lng, radiusM);
    }
}
