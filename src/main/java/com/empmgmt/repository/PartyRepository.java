package com.empmgmt.repository;

import com.empmgmt.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {
    boolean existsByCombined(String combined);
    List<Party> findTop50ByCombinedContainingIgnoreCase(String combined);
}

