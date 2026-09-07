package com.spring.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@MappedSuperclass
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class) // required for the @Created*/@LastModified* fields
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * Populated from the authenticated principal by JpaAuditingConfig#auditorProvider.
     * Nullable by design: rows written by the seeder, by a migration or by an anonymous request
     * (a signup, for instance) genuinely have no author.
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private Long modifiedBy;

    // JPA lifecycle callbacks as a backstop for entities persisted outside an auditing context
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
    public LocalDateTime getCreatedAtInZone(ZoneId zoneId) {
        return createdAt != null ? createdAt.atZone(zoneId).toLocalDateTime() : null;
    }

    public LocalDateTime getUpdatedAtInZone(ZoneId zoneId) {
        return updatedAt != null ? updatedAt.atZone(zoneId).toLocalDateTime() : null;
    }

    // Formatted display methods
    public String getCreatedAtFormatted(ZoneId zoneId) {
        return createdAt != null
                ? createdAt.atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
    }

    public String getUpdatedAtFormatted(ZoneId zoneId) {
        return updatedAt != null
                ? updatedAt.atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
    }
}
