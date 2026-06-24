package com.rrs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_model")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String maintainer;

    @Column(name = "library_id")
    private Long libraryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataModelStatus status = DataModelStatus.UNINITIALIZED;

    @Column(name = "table_name", length = 200)
    private String tableName;

    @Column(columnDefinition = "TEXT")
    private String ddl;

    @Column(name = "primary_key", length = 200)
    private String primaryKey;

    @Column(name = "last_extracted_at")
    private LocalDateTime lastExtractedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataModel dm)) return false;
        return id != null && id.equals(dm.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
