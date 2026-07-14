package com.spring.app.domain.role;

import com.spring.app.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Basic role lookup methods
    Optional<Role> findByRoleName(String roleName);

    Optional<Role> findByRoleNameAndStatus(String roleName, Status status);

    List<Role> findByStatus(Status status);

    // Role lookup with permissions
    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.roleName = :roleName")
    Optional<Role> findByRoleNameWithPermissions(@Param("roleName") String roleName);

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.roleName = :roleName AND r.status = :status")
    Optional<Role> findByRoleNameAndStatusWithPermissions(@Param("roleName") String roleName, @Param("status") Status status);

    // Multiple roles lookup
    List<Role> findByRoleNameIn(List<String> roleNames);

    @Query("SELECT r FROM Role r WHERE r.roleName IN :roleNames AND r.status = :status")
    List<Role> findByRoleNameInAndStatus(@Param("roleNames") List<String> roleNames, @Param("status") Status status);

    // Existence checks
    boolean existsByRoleName(String roleName);

    boolean existsByRoleNameAndStatus(String roleName, Status status);

    // Count methods
    long countByStatus(Status status);

    // Find roles by user
    @Query("SELECT DISTINCT r FROM Role r JOIN r.users u WHERE u.username = :username AND r.status = :status")
    Set<Role> findRolesByUsernameAndStatus(@Param("username") String username, @Param("status") Status status);

    // Find roles with specific permissions
    @Query("SELECT DISTINCT r FROM Role r JOIN r.permissions p WHERE p.permissionName = :permissionName AND r.status = :status")
    List<Role> findRolesByPermissionAndStatus(@Param("permissionName") String permissionName, @Param("status") Status status);
}