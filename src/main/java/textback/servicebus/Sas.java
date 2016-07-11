package textback.servicebus;

import java.time.LocalDateTime;

/**
 * Shared signature DTO
 */
public class Sas {

    public Sas(String token, LocalDateTime createdTime, LocalDateTime validToTime) {
        this.token = token;
        this.createdTime = createdTime;
        this.validToTime = validToTime;
    }

    String token;

    LocalDateTime createdTime;

    LocalDateTime validToTime;

    public boolean isExpired() {
        return validToTime.isBefore(LocalDateTime.now());
    }
}
