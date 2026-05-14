package co.edu.unbosque.foodik.scheduler;

import co.edu.unbosque.foodik.domain.entity.Reservation;
import co.edu.unbosque.foodik.domain.entity.TableSlot;
import co.edu.unbosque.foodik.domain.enums.ReservationStatus;
import co.edu.unbosque.foodik.repository.ReservationRepository;
import co.edu.unbosque.foodik.repository.TableSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationRepository reservationRepository;
    private final TableSlotRepository tableSlotRepository;

    @Value("${application.reservation.confirmation-timeout-minutes:15}")
    private int timeoutMinutes;

    public ReservationExpirationScheduler(ReservationRepository reservationRepository,
                                           TableSlotRepository tableSlotRepository) {
        this.reservationRepository = reservationRepository;
        this.tableSlotRepository = tableSlotRepository;
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expireUnconfirmedReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Reservation> expired = reservationRepository
                .findByStatusAndCreatedAtBefore(ReservationStatus.PENDING, cutoff);

        if (expired.isEmpty()) return;

        log.info("Expiring {} unconfirmed reservations", expired.size());
        for (Reservation reservation : expired) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            TableSlot slot = reservation.getTableSlot();
            slot.setAvailableCount(slot.getAvailableCount() + 1);
            tableSlotRepository.save(slot);
        }
        reservationRepository.saveAll(expired);
    }
}
