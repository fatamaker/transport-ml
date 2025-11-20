package com.example.entities;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Transport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; 
    private String number; 
    private String origin;
    private String destination;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime estimatedDeparture;
    private String status; 
    private int delayMinutes;

   
    public Transport() {
    }

   
    public Transport(String type, String number, String origin, String destination, LocalDateTime scheduledDeparture, String status, int delayMinutes) {
        this.type = type;
        this.number = number;
        this.origin = origin;
        this.destination = destination;
        this.scheduledDeparture = scheduledDeparture;
        this.estimatedDeparture = scheduledDeparture.plusMinutes(delayMinutes);
        this.status = status;
        this.delayMinutes = delayMinutes;
    }

    @Override
    public String toString() {
        return "Transport{" +
                "type='" + type + '\'' +
                ", number='" + number + '\'' +
                ", origin='" + origin + '\'' +
                ", destination='" + destination + '\'' +
                ", scheduledDeparture=" + scheduledDeparture +
                ", status='" + status + '\'' +
                ", delayMinutes=" + delayMinutes +
                '}';
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public LocalDateTime getScheduledDeparture() { return scheduledDeparture; }
    public void setScheduledDeparture(LocalDateTime scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; }
    public LocalDateTime getEstimatedDeparture() { return estimatedDeparture; }
    public void setEstimatedDeparture(LocalDateTime estimatedDeparture) { this.estimatedDeparture = estimatedDeparture; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(int delayMinutes) { this.delayMinutes = delayMinutes; }
    
}