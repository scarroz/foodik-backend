package co.edu.unbosque.foodik.domain.dto.response;
import java.time.LocalDateTime;
import java.util.UUID;
public record FavoriteResponse(UUID id, UUID restaurantId, String restaurantName,
                                String restaurantImageUrl, LocalDateTime createdAt) {}
