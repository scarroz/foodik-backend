package co.edu.unbosque.foodik.domain.dto.response;
import co.edu.unbosque.foodik.domain.enums.BillStatus;
import co.edu.unbosque.foodik.domain.enums.SplitMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record BillResponse(UUID id, UUID reservationId, BigDecimal totalAmount,
                            SplitMode splitMode, BillStatus status, List<BillItemResponse> items) {}
