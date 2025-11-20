package com.example;

import com.example.entities.Transport;
import com.example.repositories.TransportRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner initDatabase(TransportRepository repository) {
        return args -> {
            // Clear any existing data
            repository.deleteAll();
            
            // Add sample transports
            Transport flight1 = new Transport(
                "Vol", 
                "AF101", 
                "Paris", 
                "New York", 
                LocalDateTime.of(2025, 11, 21, 10, 0),
                "Scheduled", 
                0
            );
            
            Transport train1 = new Transport(
                "Train", 
                "TGV123", 
                "Lyon", 
                "Marseille", 
                LocalDateTime.of(2025, 11, 21, 14, 30),
                "Delayed", 
                15
            );
            
            Transport bus1 = new Transport(
                "Bus", 
                "BUS456", 
                "Nice", 
                "Cannes", 
                LocalDateTime.of(2025, 11, 21, 9, 15),
                "On Time", 
                0
            );
            
            repository.save(flight1);
            repository.save(train1);
            repository.save(bus1);
            
            System.out.println("Sample data loaded successfully!");
        };
    }
}