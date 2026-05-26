package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CreateReservationRequest(
        @NotNull UUID restaurantId,
        @NotNull UUID tableSlotId,
        @NotNull @Min(1) @Max(20) Integer partySize,
        String notes
) {}
