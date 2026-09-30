package com.cropcoders.procurement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

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
