package co.edu.unbosque.foodik.domain.dto.response;
import java.math.BigDecimal;
import java.util.UUID;
public record MenuItemResponse(UUID id, UUID restaurantId, String name, String description,
                                BigDecimal price, String imageUrl, String category, Boolean isAvailable) {}
