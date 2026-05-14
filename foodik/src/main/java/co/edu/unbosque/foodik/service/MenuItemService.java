package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.MenuItemResponse;
import java.util.List;
import java.util.UUID;
public interface MenuItemService {
    List<MenuItemResponse> findByRestaurant(UUID restaurantId);
    MenuItemResponse create(UUID restaurantId, CreateMenuItemRequest request, String adminEmail);
    MenuItemResponse update(UUID itemId, UpdateMenuItemRequest request, String adminEmail);
    void delete(UUID itemId, String adminEmail);
}
