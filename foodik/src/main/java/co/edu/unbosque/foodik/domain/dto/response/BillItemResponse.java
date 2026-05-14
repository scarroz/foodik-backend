package co.edu.unbosque.foodik.domain.dto.response;
import java.math.BigDecimal;
import java.util.UUID;
public record BillItemResponse(UUID id, UUID menuItemId, String menuItemName,
                                UUID assignedUserId, Integer quantity, BigDecimal subtotal) {}
