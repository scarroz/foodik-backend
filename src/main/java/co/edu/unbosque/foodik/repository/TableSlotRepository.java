package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.TableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public interface TableSlotRepository extends JpaRepository<TableSlot, UUID> {
    @Query("""
            SELECT ts FROM TableSlot ts WHERE ts.restaurant.id = :restaurantId
            AND ts.date = :date AND ts.availableCount > 0 AND ts.capacity >= :partySize ORDER BY ts.timeSlot
            """)
    List<TableSlot> findAvailableSlots(@Param("restaurantId") UUID restaurantId,
                                        @Param("date") LocalDate date, @Param("partySize") int partySize);
}
