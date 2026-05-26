package co.edu.unbosque.foodik.domain.dto.response;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
public record ReservationResponse(UUID id, UUID userId, String userName, UUID restaurantId, String restaurantName,
                                  UUID tableSlotId, Integer partySize, ReservationStatus status,
                                  LocalDate reservationDate, LocalTime reservationTime,
                                  String notes, LocalDateTime createdAt) {}