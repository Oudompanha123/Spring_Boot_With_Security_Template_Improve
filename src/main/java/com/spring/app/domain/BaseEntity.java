package com.spring.app.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class) // This is required!
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA lifecycle callbacks as backup (will be overridden by Spring Data auditing)
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Helper methods for timezone-aware display
    public java.time.LocalDateTime getCreatedAtInZone(java.time.ZoneId zoneId) {
        return createdAt != null ? createdAt.atZone(zoneId).toLocalDateTime() : null;
    }

    public java.time.LocalDateTime getUpdatedAtInZone(java.time.ZoneId zoneId) {
        return updatedAt != null ? updatedAt.atZone(zoneId).toLocalDateTime() : null;
    }

    // Formatted display methods
    public String getCreatedAtFormatted(java.time.ZoneId zoneId) {
        return createdAt != null
                ? createdAt.atZone(zoneId).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
    }

    public String getUpdatedAtFormatted(java.time.ZoneId zoneId) {
        return updatedAt != null
                ? updatedAt.atZone(zoneId).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
    }

}