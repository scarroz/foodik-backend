package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.*;
import java.util.UUID;
public interface BillService {
    BillResponse create(CreateBillRequest request, String userEmail);
    BillResponse findById(UUID id, String userEmail);
    BillSummaryResponse split(UUID billId, SplitBillRequest request, String userEmail);
    BillSummaryResponse getSummary(UUID billId, String userEmail);
}
