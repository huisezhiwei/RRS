package com.rrs.repository;

import com.rrs.entity.AiAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAgentRepository extends JpaRepository<AiAgent, Long> {

    Optional<AiAgent> findByCode(String code);
}
