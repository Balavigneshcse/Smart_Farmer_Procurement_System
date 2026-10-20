package com.cropcoders.procurement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;
import com.cropcoders.procurement.security.AuthUser;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * The staff account for a login attempt (see AuthService.staffLogin). Wrong passwords are counted
     * in login_attempts, not on this row, so the row does not need to be locked.
     */
    Optional<User> findForLoginByUsername(String username);

    /**
     * The logged-in user as the database has them now: id, role, name and centre, or empty if the
     * account is switched off or does not exist. Read on every logged-in request (JwtAuthFilter).
     */
    @Query("""
            select new com.cropcoders.procurement.security.AuthUser(u.id, u.role, u.name, c.id)
            from User u left join u.centre c
            where u.id = :id and u.active = true""")
    Optional<AuthUser> findActiveAuthUser(@Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    /** Admin and staff accounts of every centre, grouped by centre (super admin's list). */
    @Query("""
            select u from User u join fetch u.centre c
            where u.role in :roles
            order by c.name, u.role, u.name""")
    List<User> findStaff(@Param("roles") Collection<Role> roles);

    @Query("""
            select u from User u join fetch u.centre c
            where u.role = :role and c.id = :centreId
            order by u.name""")
    List<User> findStaffOfCentre(@Param("role") Role role, @Param("centreId") Long centreId);
}
