package co.edu.unbosque.foodik.service.strategy;

import co.edu.unbosque.foodik.domain.dto.request.ChainLinkRequest;
import co.edu.unbosque.foodik.domain.dto.request.SplitBillRequest;
import co.edu.unbosque.foodik.domain.dto.response.BillSummaryResponse;
import co.edu.unbosque.foodik.domain.entity.Bill;
import co.edu.unbosque.foodik.domain.entity.BillItem;
import co.edu.unbosque.foodik.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class ChainedSplitStrategy implements BillSplitStrategy {
    @Override
    public BillSummaryResponse split(Bill bill, SplitBillRequest request) {
        List<ChainLinkRequest> chain = request.chain();
        if (chain == null || chain.isEmpty())
            throw new ValidationException("CHAINED split requires at least one chain link");
        validateNoCycles(chain);
        Map<UUID, BigDecimal> consumed = new HashMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getAssignedUser() == null) continue;
            consumed.merge(item.getAssignedUser().getId(), item.getSubtotal(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> distribution = new HashMap<>();
        Set<UUID> payers = new HashSet<>();
        Set<UUID> beneficiaries = new HashSet<>();
        for (ChainLinkRequest link : chain) {
            payers.add(link.payerId());
            beneficiaries.add(link.beneficiaryId());
            distribution.merge(link.payerId(),
                    consumed.getOrDefault(link.beneficiaryId(), BigDecimal.ZERO), BigDecimal::add);
        }
        beneficiaries.stream().filter(b -> !payers.contains(b)).forEach(last ->
                distribution.merge(last, consumed.getOrDefault(last, BigDecimal.ZERO), BigDecimal::add));
        return new BillSummaryResponse(bill.getId(), distribution, bill.getTotalAmount());
    }

    private void validateNoCycles(List<ChainLinkRequest> chain) {
        Map<UUID, UUID> graph = new HashMap<>();
        for (ChainLinkRequest link : chain) {
            if (graph.containsKey(link.payerId()))
                throw new ValidationException("Each payer can only appear once in the chain");
            graph.put(link.payerId(), link.beneficiaryId());
        }
        for (UUID start : graph.keySet()) {
            Set<UUID> visited = new HashSet<>();
            UUID current = start;
            while (current != null && graph.containsKey(current)) {
                if (!visited.add(current))
                    throw new ValidationException("Cycle detected in payment chain");
                current = graph.get(current);
            }
        }
    }
}
