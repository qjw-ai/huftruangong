package com.aicust.repository;

import com.aicust.model.DigitalHumanConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DigitalHumanConfigRepository extends JpaRepository<DigitalHumanConfig, Long> {

    Optional<DigitalHumanConfig> findByIsActiveTrue();

    Optional<DigitalHumanConfig> findByIsDefaultTrue();

    List<DigitalHumanConfig> findByAvatarType(String avatarType);
}
