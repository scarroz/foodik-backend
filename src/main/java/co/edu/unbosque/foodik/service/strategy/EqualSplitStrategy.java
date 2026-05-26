package co.edu.unbosque.foodik.service.strategy;

import co.edu.unbosque.foodik.domain.dto.request.SplitBillRequest;
import co.edu.unbosque.foodik.domain.dto.response.BillSummaryResponse;
import co.edu.unbosque.foodik.domain.entity.Bill;
import co.edu.unbosque.foodik.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EqualSplitStrategy implements BillSplitStrategy {
    @Override
    public BillSummaryResponse split(Bill bill, SplitBillRequest request) {
        List<UUID> participants = request.participants();
        if (participants == null || participants.isEmpty())
            throw new ValidationException("EQUAL split requires at least one participant");
        int n = participants.size();
        BigDecimal total = bill.getTotalAmount();
        BigDecimal perPerson = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.FLOOR);
        BigDecimal remainder = total.subtract(perPerson.multiply(BigDecimal.valueOf(n)));
        Map<UUID, BigDecimal> distribution = new HashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            distribution.put(participants.get(i), i == 0 ? perPerson.add(remainder) : perPerson);
        }
        return new BillSummaryResponse(bill.getId(), distribution, total);
    }
}
