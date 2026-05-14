package co.edu.unbosque.foodik.domain.dto.response;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import java.util.Map;
import java.util.UUID;
public record RestaurantResponse(
        UUID id, String name, String description, String address,
        Double latitude, Double longitude, String phone, String email,
        String imageUrl, RestaurantCategory category,
        Map<String, String> openingHours, Boolean isActive, Double distanceKm
) {}
