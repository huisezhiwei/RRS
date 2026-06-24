package com.rrs.repository;

import com.rrs.entity.ExtractionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExtractionLogRepository extends JpaRepository<ExtractionLog, Long> {

    Page<ExtractionLog> findByModelIdOrderByCreatedAtDesc(Long modelId, Pageable pageable);

    Optional<ExtractionLog> findTopByModelIdOrderByCreatedAtDesc(Long modelId);
}
