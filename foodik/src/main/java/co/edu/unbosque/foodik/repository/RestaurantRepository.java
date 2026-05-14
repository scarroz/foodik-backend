package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.Restaurant;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.isActive = true
            AND r.latitude BETWEEN :minLat AND :maxLat
            AND r.longitude BETWEEN :minLng AND :maxLng
            AND (:category IS NULL OR r.category = :category)
            """)
    List<Restaurant> findInBoundingBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                        @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                                        @Param("category") RestaurantCategory category);
    List<Restaurant> findByOwnerIdAndIsActiveTrue(UUID ownerId);
}
