package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserId(UUID userId);
    Optional<Favorite> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId);
    boolean existsByUserIdAndRestaurantId(UUID userId, UUID restaurantId);
    void deleteByUserIdAndRestaurantId(UUID userId, UUID restaurantId);
}
