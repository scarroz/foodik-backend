package co.edu.unbosque.foodik.domain.dto.request;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import jakarta.validation.constraints.*;
import java.util.Map;
public record CreateRestaurantRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotBlank @Size(max = 250) String address,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        String imageUrl,
        @NotNull RestaurantCategory category,
        Map<String, String> openingHours
) {}
