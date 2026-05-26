package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.CreateDiscountRequest;
import co.edu.unbosque.foodik.domain.dto.response.DiscountResponse;
import java.util.List;
import java.util.UUID;
public interface DiscountService {
    List<DiscountResponse> findActiveByRestaurant(UUID restaurantId);
    DiscountResponse create(UUID restaurantId, CreateDiscountRequest request, String adminEmail);
    void delete(UUID discountId, String adminEmail);
}
