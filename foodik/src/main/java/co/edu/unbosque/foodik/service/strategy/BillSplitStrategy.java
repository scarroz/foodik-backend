package co.edu.unbosque.foodik.service.strategy;
import co.edu.unbosque.foodik.domain.dto.request.SplitBillRequest;
import co.edu.unbosque.foodik.domain.dto.response.BillSummaryResponse;
import co.edu.unbosque.foodik.domain.entity.Bill;
public interface BillSplitStrategy {
    BillSummaryResponse split(Bill bill, SplitBillRequest request);
}
