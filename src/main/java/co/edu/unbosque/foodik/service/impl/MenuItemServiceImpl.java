package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.MenuItemResponse;
import co.edu.unbosque.foodik.domain.entity.MenuItem;
import co.edu.unbosque.foodik.domain.entity.Restaurant;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.MenuItemRepository;
import co.edu.unbosque.foodik.repository.RestaurantRepository;
import co.edu.unbosque.foodik.service.MenuItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
@Service
public class MenuItemServiceImpl implements MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    public MenuItemServiceImpl(MenuItemRepository m, RestaurantRepository r) { this.menuItemRepository = m; this.restaurantRepository = r; }
    @Override @Transactional(readOnly = true)
    public List<MenuItemResponse> findByRestaurant(UUID restaurantId) {
        return menuItemRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId).stream().map(this::toResponse).toList();
    }
    @Override @Transactional
    public MenuItemResponse create(UUID restaurantId, CreateMenuItemRequest request, String adminEmail) {
        Restaurant restaurant = getOwned(restaurantId, adminEmail);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant); item.setName(request.name()); item.setDescription(request.description());
        item.setPrice(request.price()); item.setImageUrl(request.imageUrl()); item.setCategory(request.category());
        return toResponse(menuItemRepository.save(item));
    }
    @Override @Transactional
    public MenuItemResponse update(UUID itemId, UpdateMenuItemRequest request, String adminEmail) {
        MenuItem item = menuItemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("MenuItem", itemId));
        if (!item.getRestaurant().getOwner().getEmail().equals(adminEmail)) throw new UnauthorizedException("Access denied");
        if (request.name() != null) item.setName(request.name());
        if (request.description() != null) item.setDescription(request.description());
        if (request.price() != null) item.setPrice(request.price());
        if (request.imageUrl() != null) item.setImageUrl(request.imageUrl());
        if (request.category() != null) item.setCategory(request.category());
        if (request.isAvailable() != null) item.setIsAvailable(request.isAvailable());
        return toResponse(menuItemRepository.save(item));
    }
    @Override @Transactional
    public void delete(UUID itemId, String adminEmail) {
        MenuItem item = menuItemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("MenuItem", itemId));
        if (!item.getRestaurant().getOwner().getEmail().equals(adminEmail)) throw new UnauthorizedException("Access denied");
        item.setIsAvailable(false); menuItemRepository.save(item);
    }
    private Restaurant getOwned(UUID id, String email) {
        Restaurant r = restaurantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
        if (!r.getOwner().getEmail().equals(email)) throw new UnauthorizedException("Access denied");
        return r;
    }
    private MenuItemResponse toResponse(MenuItem m) {
        return new MenuItemResponse(m.getId(), m.getRestaurant().getId(), m.getName(),
                m.getDescription(), m.getPrice(), m.getImageUrl(), m.getCategory(), m.getIsAvailable());
    }
}
