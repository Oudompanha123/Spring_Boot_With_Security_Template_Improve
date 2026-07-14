package com.spring.app.domain.permission;

import com.spring.app.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    // Basic permission lookup methods
    Optional<Permission> findByPermissionName(String permissionName);
    
    Optional<Permission> findByPermissionNameAndStatus(String permissionName, Status status);
    
    List<Permission> findByStatus(Status status);
    
    // Find by resource and action
    List<Permission> findByResource(String resource);
    
    List<Permission> findByResourceAndAction(String resource, String action);
    
    Optional<Permission> findByResourceAndActionAndStatus(String resource, String action, Status status);
    
    // Multiple permissions lookup
    List<Permission> findByPermissionNameIn(List<String> permissionNames);
    
    @Query("SELECT p FROM Permission p WHERE p.permissionName IN :permissionNames AND p.status = :status")
    List<Permission> findByPermissionNameInAndStatus(@Param("permissionNames") List<String> permissionNames, @Param("status") Status status);
    
    // Existence checks
    boolean existsByPermissionName(String permissionName);
    
    boolean existsByPermissionNameAndStatus(String permissionName, Status status);
    
    boolean existsByResourceAndAction(String resource, String action);
    
    // Count methods
    long countByStatus(Status status);
    
    long countByResource(String resource);
    
    // Find permissions by user (through roles)
    @Query("SELECT DISTINCT p FROM Permission p JOIN p.roles r JOIN r.users u WHERE u.username = :username AND p.status = :status")
    Set<Permission> findPermissionsByUsernameAndStatus(@Param("username") String username, @Param("status") Status status);
    
    // Find permissions by role
    @Query("SELECT DISTINCT p FROM Permission p JOIN p.roles r WHERE r.roleName = :roleName AND p.status = :status")
    Set<Permission> findPermissionsByRoleNameAndStatus(@Param("roleName") String roleName, @Param("status") Status status);
}