package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
public interface DiscountRepository extends JpaRepository<Discount, UUID> {
    @Query("""
            SELECT d FROM Discount d WHERE d.restaurant.id = :restaurantId
            AND d.isActive = true AND d.validFrom <= :now AND d.validTo >= :now
            """)
    List<Discount> findActiveByRestaurant(@Param("restaurantId") UUID restaurantId, @Param("now") LocalDateTime now);
}
