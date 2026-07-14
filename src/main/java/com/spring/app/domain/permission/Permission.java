package com.spring.app.domain.permission;

import com.spring.app.domain.BaseEntity;
import com.spring.app.domain.role.Role;
import com.spring.app.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions", indexes = {
        @Index(name = "idx_permission_name", columnList = "permission_name"),
        @Index(name = "idx_permission_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "roles")
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_name", unique = true, nullable = false, length = 100)
    private String permissionName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "resource", length = 100)
    private String resource;

    @Column(name = "action", length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    // Many-to-Many relationship with Role
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Convenience methods
    public void addRole(Role role) {
        roles.add(role);
        role.getPermissions().add(this);
    }

    public void removeRole(Role role) {
        roles.remove(role);
        role.getPermissions().remove(this);
    }
}