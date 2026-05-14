package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByRestaurantIdAndIsAvailableTrue(UUID restaurantId);
    List<MenuItem> findByRestaurantId(UUID restaurantId);
}
