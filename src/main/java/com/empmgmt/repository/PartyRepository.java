package com.empmgmt.repository;

import com.empmgmt.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {
    boolean existsByCombined(String combined);
    Optional<Party> findByCombined(String combined);
    List<Party> findTop50ByCombinedContainingIgnoreCase(String combined);
    List<Party> findTop50ByNameContainingIgnoreCaseOrTrailingNumberContainingIgnoreCase(String name, String trailingNumber);
    List<Party> findByCombinedIn(Collection<String> combined);
    List<Party> findByWhatsappOptInTrueAndPhoneNotNull();
    long countByPhoneIsNotNull();
    long countByWhatsappOptInTrueAndPhoneIsNotNull();
}

