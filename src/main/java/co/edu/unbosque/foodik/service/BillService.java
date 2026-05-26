package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.*;
import java.util.List;
import java.util.UUID;
public interface BillService {
    BillResponse create(CreateBillRequest request, String userEmail);
    BillResponse findById(UUID id, String userEmail);
    BillSummaryResponse split(UUID billId, SplitBillRequest request, String userEmail);
    BillSummaryResponse getSummary(UUID billId, String userEmail);
    BillResponse findByReservation(UUID reservationId, String email);
    List<BillResponse> getMyBills(String email);
}