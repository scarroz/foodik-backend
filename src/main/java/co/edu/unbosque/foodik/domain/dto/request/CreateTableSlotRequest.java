package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
public record CreateTableSlotRequest(
        @NotNull @Min(1) Integer capacity,
        @NotNull LocalDate date,
        @NotNull LocalTime timeSlot,
        @NotNull @Min(1) Integer availableCount
) {}
