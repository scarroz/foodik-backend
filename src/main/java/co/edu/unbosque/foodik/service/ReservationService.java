package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.CreateReservationRequest;
import co.edu.unbosque.foodik.domain.dto.response.*;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public interface ReservationService {
    List<TableSlotResponse> getAvailability(UUID restaurantId, LocalDate date, int partySize);
    ReservationResponse create(CreateReservationRequest request, String userEmail);
    Page<ReservationResponse> getMyReservations(String userEmail, Pageable pageable);
    ReservationResponse findById(UUID id, String userEmail);
    ReservationResponse cancel(UUID id, String userEmail);
    Page<ReservationResponse> getRestaurantReservations(UUID restaurantId, String adminEmail, Pageable pageable);
    ReservationResponse updateStatus(UUID id, ReservationStatus status, String adminEmail);
}
