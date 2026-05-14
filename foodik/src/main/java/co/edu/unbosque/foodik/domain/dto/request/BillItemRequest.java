package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record BillItemRequest(
        @NotNull UUID menuItemId,
        UUID assignedUserId,
        @NotNull @Min(1) Integer quantity
) {}
