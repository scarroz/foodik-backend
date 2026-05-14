package co.edu.unbosque.foodik.service.impl;

import co.edu.unbosque.foodik.domain.dto.request.CreateReservationRequest;
import co.edu.unbosque.foodik.domain.dto.response.ReservationResponse;
import co.edu.unbosque.foodik.domain.dto.response.TableSlotResponse;
import co.edu.unbosque.foodik.domain.entity.*;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import co.edu.unbosque.foodik.exception.ConflictException;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.*;
import co.edu.unbosque.foodik.service.ReservationService;
import co.edu.unbosque.foodik.util.NotificationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final TableSlotRepository tableSlotRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final NotificationUtil notificationUtil;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                   TableSlotRepository tableSlotRepository,
                                   UserRepository userRepository,
                                   RestaurantRepository restaurantRepository,
                                   NotificationUtil notificationUtil) {
        this.reservationRepository = reservationRepository;
        this.tableSlotRepository = tableSlotRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.notificationUtil = notificationUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableSlotResponse> getAvailability(UUID restaurantId, LocalDate date, int partySize) {
        return tableSlotRepository.findAvailableSlots(restaurantId, date, partySize)
                .stream().map(this::toSlotResponse).toList();
    }

    /**
     * Crea una reserva usando optimistic locking de JPA (@Version en TableSlot).
     * Si dos transacciones concurrentes intentan decrementar el mismo slot,
     * la segunda lanza ObjectOptimisticLockingFailureException → ConflictException.
     */
    @Override
    @Transactional
    public ReservationResponse create(CreateReservationRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.restaurantId()));
        TableSlot slot = tableSlotRepository.findById(request.tableSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("TableSlot", request.tableSlotId()));

        if (slot.getAvailableCount() <= 0)
            throw new ConflictException("No availability for the selected slot");
        if (slot.getCapacity() < request.partySize())
            throw new ConflictException("Party size exceeds table capacity of " + slot.getCapacity());

        try {
            slot.setAvailableCount(slot.getAvailableCount() - 1);
            tableSlotRepository.saveAndFlush(slot);

            Reservation reservation = new Reservation();
            reservation.setUser(user);
            reservation.setRestaurant(restaurant);
            reservation.setTableSlot(slot);
            reservation.setPartySize(request.partySize());
            reservation.setReservationDate(slot.getDate());
            reservation.setReservationTime(slot.getTimeSlot());
            reservation.setNotes(request.notes());

            reservation = reservationRepository.save(reservation);
            notificationUtil.sendReservationConfirmation(reservation);
            return toResponse(reservation);

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("The slot was just reserved by another user. Please choose a different time.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return reservationRepository.findByUserId(user.getId(), pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse findById(UUID id, String userEmail) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        if (!reservation.getUser().getEmail().equals(userEmail))
            throw new UnauthorizedException("Access denied to this reservation");
        return toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse cancel(UUID id, String userEmail) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        if (!reservation.getUser().getEmail().equals(userEmail))
            throw new UnauthorizedException("Access denied to this reservation");
        if (reservation.getStatus() == ReservationStatus.CANCELLED)
            throw new ConflictException("Reservation is already cancelled");
        if (reservation.getStatus() == ReservationStatus.COMPLETED)
            throw new ConflictException("Cannot cancel a completed reservation");

        reservation.setStatus(ReservationStatus.CANCELLED);
        TableSlot slot = reservation.getTableSlot();
        slot.setAvailableCount(slot.getAvailableCount() + 1);
        tableSlotRepository.save(slot);
        notificationUtil.sendReservationCancellation(reservation);
        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getRestaurantReservations(UUID restaurantId, String adminEmail, Pageable pageable) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwner().getEmail().equals(adminEmail))
            throw new UnauthorizedException("Access denied to this restaurant's reservations");
        return reservationRepository.findByRestaurantId(restaurantId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ReservationResponse updateStatus(UUID id, ReservationStatus status, String adminEmail) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        if (!reservation.getRestaurant().getOwner().getEmail().equals(adminEmail))
            throw new UnauthorizedException("Access denied");
        reservation.setStatus(status);
        return toResponse(reservationRepository.save(reservation));
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(r.getId(), r.getUser().getId(), r.getRestaurant().getId(),
                r.getRestaurant().getName(), r.getTableSlot().getId(), r.getPartySize(),
                r.getStatus(), r.getReservationDate(), r.getReservationTime(),
                r.getNotes(), r.getCreatedAt());
    }

    private TableSlotResponse toSlotResponse(TableSlot s) {
        return new TableSlotResponse(s.getId(), s.getRestaurant().getId(),
                s.getCapacity(), s.getDate(), s.getTimeSlot(), s.getAvailableCount());
    }
}
