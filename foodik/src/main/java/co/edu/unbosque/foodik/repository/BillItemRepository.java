package co.edu.unbosque.foodik.repository;
import co.edu.unbosque.foodik.domain.entity.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface BillItemRepository extends JpaRepository<BillItem, UUID> {
    List<BillItem> findByBillId(UUID billId);
}
