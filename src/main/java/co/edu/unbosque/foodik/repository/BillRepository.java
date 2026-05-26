package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface BillRepository extends JpaRepository<Bill, UUID> {
    Optional<Bill> findByReservationId(UUID reservationId);
    List<Bill> findByReservationUserId(UUID userId);

}
