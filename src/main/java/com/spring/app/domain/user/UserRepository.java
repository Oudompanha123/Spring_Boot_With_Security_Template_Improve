package com.spring.app.domain.user;

import com.spring.app.enums.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Basic user lookup methods used by UserService
    Optional<User> findByUsernameAndStatus(String username, Status status);

    Optional<User> findByEmailAndStatus(String email, Status status);

    // Combined method for username OR email lookup
    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT u FROM User u WHERE (u.username = :identifier OR u.email = :identifier) AND u.status = :status")
    Optional<User> findByUsernameOrEmailAndStatus(@Param("identifier") String identifier, @Param("status") Status status);

    // Method with separate parameters for username and email (more explicit)
    @Query("SELECT u FROM User u WHERE (u.username = :username AND u.status = :usernameStatus) OR (u.email = :email AND u.status = :emailStatus)")
    Optional<User> findByUsernameAndStatusOrEmailAndStatus(
            @Param("username") String username,
            @Param("usernameStatus") Status usernameStatus,
            @Param("email") String email,
            @Param("emailStatus") Status emailStatus
    );

    // Methods with JOIN FETCH for loading roles and permissions (used in AuthService)
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.username = :username")
    Optional<User> findByUsernameWithRolesAndPermissions(@Param("username") String username);

    // Methods with JOIN FETCH for loading roles and permissions by user id (used in AuthService)
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.userId = :id AND u.status = 'ACTIVE'")
    Optional<User> findByUsernameWithRolesAndPermissionsByUserId(@Param("id") Long id);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<User> findByEmailWithRolesAndPermissions(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE (u.username = :identifier OR u.email = :identifier)")
    Optional<User> findByUsernameOrEmailWithRolesAndPermissions(@Param("identifier") String identifier);

    // Existence check methods (used in registration)
    boolean existsByUsernameAndStatus(String username, Status status);

    boolean existsByEmailAndStatus(String email, Status status);

    // Additional useful methods
//    @Query("SELECT u FROM User u WHERE u.username = :username")
//    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    // Count methods for statistics
    long countByStatus(Status status);

    // Find users by role
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.roleName = :roleName AND u.status = :status")
    Optional<User> findByRoleNameAndStatus(@Param("roleName") String roleName, @Param("status") Status status);


    @Query("select u FROM User u where u.username = ?1 and u.status = 'ACTIVE'")
    Optional<User> findByUsername(String username);

    @Query("select u FROM User u where u.userId = ?1 and u.status = 'ACTIVE'")
    Optional<User> findByUserId(Long id);
}