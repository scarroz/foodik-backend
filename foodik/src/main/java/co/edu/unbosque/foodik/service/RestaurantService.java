package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.RestaurantResponse;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
public interface RestaurantService {
    Page<RestaurantResponse> findNearby(double lat, double lng, double radiusKm, RestaurantCategory category, Pageable pageable);
    RestaurantResponse findById(UUID id);
    RestaurantResponse create(CreateRestaurantRequest request, String ownerEmail);
    RestaurantResponse update(UUID id, UpdateRestaurantRequest request, String ownerEmail);
    void delete(UUID id, String ownerEmail);
}
