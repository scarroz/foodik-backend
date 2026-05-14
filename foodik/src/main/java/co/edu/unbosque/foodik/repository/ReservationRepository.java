package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.Reservation;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Page<Reservation> findByUserId(UUID userId, Pageable pageable);
    Page<Reservation> findByRestaurantId(UUID restaurantId, Pageable pageable);
    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.createdAt < :before")
    List<Reservation> findByStatusAndCreatedAtBefore(@Param("status") ReservationStatus status,
                                                      @Param("before") LocalDateTime before);
}
