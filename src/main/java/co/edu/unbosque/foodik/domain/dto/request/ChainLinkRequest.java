package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ChainLinkRequest(@NotNull UUID payerId, @NotNull UUID beneficiaryId) {}
