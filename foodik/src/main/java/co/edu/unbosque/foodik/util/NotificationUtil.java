package co.edu.unbosque.foodik.util;

import co.edu.unbosque.foodik.domain.entity.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationUtil {

    private static final Logger log = LoggerFactory.getLogger(NotificationUtil.class);
    private final JavaMailSender mailSender;

    public NotificationUtil(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendReservationConfirmation(Reservation reservation) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(reservation.getUser().getEmail());
            message.setSubject("FOODIK — Reservation Confirmed");
            message.setText("Hi " + reservation.getUser().getName() + ",\n\n"
                    + "Your reservation at " + reservation.getRestaurant().getName() + " is confirmed!\n"
                    + "Date: " + reservation.getReservationDate() + " at " + reservation.getReservationTime()
                    + "\nParty size: " + reservation.getPartySize() + "\n\n— The FOODIK Team");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send confirmation email: {}", e.getMessage());
        }
    }

    @Async
    public void sendReservationCancellation(Reservation reservation) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(reservation.getUser().getEmail());
            message.setSubject("FOODIK — Reservation Cancelled");
            message.setText("Your reservation at " + reservation.getRestaurant().getName()
                    + " on " + reservation.getReservationDate() + " has been cancelled.");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send cancellation email: {}", e.getMessage());
        }
    }
}
