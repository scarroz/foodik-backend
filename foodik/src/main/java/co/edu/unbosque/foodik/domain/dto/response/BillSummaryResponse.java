package co.edu.unbosque.foodik.domain.dto.response;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
public record BillSummaryResponse(UUID billId, Map<UUID, BigDecimal> amountPerUser, BigDecimal totalAmount) {}
