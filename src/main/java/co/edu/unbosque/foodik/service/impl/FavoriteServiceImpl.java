package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.response.FavoriteResponse;
import co.edu.unbosque.foodik.domain.entity.Favorite;
import co.edu.unbosque.foodik.domain.entity.Restaurant;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.exception.ConflictException;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.repository.FavoriteRepository;
import co.edu.unbosque.foodik.repository.RestaurantRepository;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.service.FavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
@Service
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    public FavoriteServiceImpl(FavoriteRepository f, UserRepository u, RestaurantRepository r) {
        this.favoriteRepository = f; this.userRepository = u; this.restaurantRepository = r;
    }
    @Override @Transactional(readOnly = true)
    public List<FavoriteResponse> getMyFavorites(String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return favoriteRepository.findByUserId(user.getId()).stream().map(this::toResponse).toList();
    }
    @Override @Transactional
    public FavoriteResponse add(UUID restaurantId, String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (favoriteRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId))
            throw new ConflictException("Restaurant is already in favorites");
        Favorite f = new Favorite(); f.setUser(user); f.setRestaurant(restaurant);
        return toResponse(favoriteRepository.save(f));
    }
    @Override @Transactional
    public void remove(UUID restaurantId, String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        favoriteRepository.deleteByUserIdAndRestaurantId(user.getId(), restaurantId);
    }
    private FavoriteResponse toResponse(Favorite f) {
        return new FavoriteResponse(f.getId(), f.getRestaurant().getId(),
                f.getRestaurant().getName(), f.getRestaurant().getImageUrl(), f.getCreatedAt());
    }
}
