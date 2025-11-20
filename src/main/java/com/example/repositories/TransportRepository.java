package com.example.repositories;


import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entities.Transport;

import java.util.List;

public interface TransportRepository extends JpaRepository<Transport, Long> {
    List<Transport> findByStatus(String status);
    Transport findByNumber(String number);
}
