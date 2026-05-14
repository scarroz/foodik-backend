package co.edu.unbosque.foodik.service.strategy;

import co.edu.unbosque.foodik.domain.dto.request.SplitBillRequest;
import co.edu.unbosque.foodik.domain.dto.response.BillSummaryResponse;
import co.edu.unbosque.foodik.domain.entity.Bill;
import co.edu.unbosque.foodik.domain.entity.BillItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class IndividualSplitStrategy implements BillSplitStrategy {
    @Override
    public BillSummaryResponse split(Bill bill, SplitBillRequest request) {
        Map<UUID, BigDecimal> distribution = new HashMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getAssignedUser() == null) continue;
            UUID userId = item.getAssignedUser().getId();
            distribution.merge(userId, item.getSubtotal(), BigDecimal::add);
        }
        return new BillSummaryResponse(bill.getId(), distribution, bill.getTotalAmount());
    }
}
