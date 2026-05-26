package co.edu.unbosque.foodik.domain.dto.request;
import co.edu.unbosque.foodik.domain.enums.SplitMode;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
public record SplitBillRequest(
        @NotNull SplitMode splitMode,
        List<UUID> participants,
        List<ChainLinkRequest> chain
) {}
