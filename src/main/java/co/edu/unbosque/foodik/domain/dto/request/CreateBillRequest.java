package co.edu.unbosque.foodik.domain.dto.request;
import co.edu.unbosque.foodik.domain.enums.SplitMode;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record CreateBillRequest(
        @NotNull UUID reservationId,
        @NotNull SplitMode splitMode,
        @NotNull @Size(min = 1) List<BillItemRequest> items
) {}
