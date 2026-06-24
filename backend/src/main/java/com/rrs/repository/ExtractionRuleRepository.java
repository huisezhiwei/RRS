package com.rrs.repository;

import com.rrs.entity.ExtractionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractionRuleRepository extends JpaRepository<ExtractionRule, Long> {

    Optional<ExtractionRule> findByModelIdAndActiveTrue(Long modelId);

    List<ExtractionRule> findByModelIdOrderByVersionDesc(Long modelId);

    long countByModelId(Long modelId);
}
