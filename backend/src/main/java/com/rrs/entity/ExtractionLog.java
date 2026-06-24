package com.rrs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_log")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtractionStatus status = ExtractionStatus.RUNNING;

    @Column(name = "total_files")
    private Integer totalFiles = 0;

    @Column(name = "processed_files")
    private Integer processedFiles = 0;

    @Column(name = "success_records")
    private Integer successRecords = 0;

    @Column(name = "failed_records")
    private Integer failedRecords = 0;

    @Column(name = "log_content", columnDefinition = "TEXT")
    private String logContent;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
