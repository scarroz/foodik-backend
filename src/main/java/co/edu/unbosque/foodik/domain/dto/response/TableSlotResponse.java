package co.edu.unbosque.foodik.domain.dto.response;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
public record TableSlotResponse(UUID id, UUID restaurantId, Integer capacity,
                                 LocalDate date, LocalTime timeSlot, Integer availableCount) {}
