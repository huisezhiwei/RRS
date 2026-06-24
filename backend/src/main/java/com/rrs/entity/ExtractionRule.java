package com.rrs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_rule")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private ExtractionRuleType ruleType;

    @Column(name = "rule_content", nullable = false, columnDefinition = "TEXT")
    private String ruleContent;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
