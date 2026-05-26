package co.edu.unbosque.foodik.domain.dto.request;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import jakarta.validation.constraints.*;
import java.util.Map;
public record UpdateRestaurantRequest(
        @Size(max = 150) String name,
        String description,
        @Size(max = 250) String address,
        Double latitude,
        Double longitude,
        @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        String imageUrl,
        RestaurantCategory category,
        Map<String, String> openingHours,
        Boolean isActive
) {}
