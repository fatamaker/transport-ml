package com.example.tools;

import com.example.entities.Transport;
import com.example.repositories.TransportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class TransportTools {

    private final TransportRepository transportRepository;

    @Bean
    @Description("Retourne la liste des transports actuellement en retard")
    public Function<Void, List<TransportInfo>> getDelayedTransports() {
        return unused -> transportRepository.findByStatus("Delayed").stream()
                .map(t -> new TransportInfo(
                        t.getNumber(),
                        t.getType(),
                        t.getOrigin(),
                        t.getDestination(),
                        t.getStatus(),
                        t.getDelayMinutes(),
                        t.getScheduledDeparture(),
                        t.getEstimatedDeparture()
                ))
                .collect(Collectors.toList());
    }

    @Bean
    @Description("Donne les détails d'un transport par son numéro")
    public Function<TransportRequest, String> getTransportDetailsByNumber() {
        return request -> {
            Transport t = transportRepository.findByNumber(request.number());
            if (t == null) {
                return "No transport found with number: " + request.number();
            }

            if (t.getDelayMinutes() > 0) {
                return String.format(
                        "%s %s (%s to %s) is currently %s with a delay of %d minutes. Estimated departure: %s.",
                        t.getType(), t.getNumber(), t.getOrigin(), t.getDestination(),
                        t.getStatus(), t.getDelayMinutes(), t.getEstimatedDeparture()
                );
            } else {
                return String.format(
                        "%s %s (%s to %s) is currently %s on schedule. Scheduled departure: %s.",
                        t.getType(), t.getNumber(), t.getOrigin(), t.getDestination(),
                        t.getStatus(), t.getScheduledDeparture()
                );
            }
        };
    }

    public record TransportRequest(String number) {}
    public record TransportInfo(
            String number,
            String type,
            String origin,
            String destination,
            String status,
            int delayMinutes,
            java.time.LocalDateTime scheduledDeparture,
            java.time.LocalDateTime estimatedDeparture
    ) {}
}