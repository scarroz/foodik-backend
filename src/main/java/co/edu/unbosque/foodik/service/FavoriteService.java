package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.response.FavoriteResponse;
import java.util.List;
import java.util.UUID;
public interface FavoriteService {
    List<FavoriteResponse> getMyFavorites(String userEmail);
    FavoriteResponse add(UUID restaurantId, String userEmail);
    void remove(UUID restaurantId, String userEmail);
}
