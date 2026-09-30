package com.cropcoders.procurement.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.StaffRequest;
import com.cropcoders.procurement.dto.StaffUpdateRequest;
import com.cropcoders.procurement.dto.StaffView;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.UserRepository;
import com.cropcoders.procurement.security.AccessScope;
import com.cropcoders.procurement.security.AuthUser;

/**
 * Admin and staff accounts.
 *   Super admin:  adds centre admins (and staff) for any centre.
 *   Centre admin: adds staff for their own centre only.
 * Accounts are switched off instead of deleted, because weighments and payments point to them.
 */
@Service
@Transactional
public class StaffService {

    private final UserRepository userRepository;
    private final CentreRepository centreRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(UserRepository userRepository, CentreRepository centreRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.centreRepository = centreRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffView> list(AuthUser me) {
        List<User> users = AccessScope.isSuperAdmin(me)
                ? userRepository.findStaff(List.of(Role.ADMIN, Role.OPERATOR))
                : userRepository.findStaffOfCentre(Role.OPERATOR, me.centreId());
        return users.stream().map(StaffView::from).toList();
    }

    public StaffView create(AuthUser me, StaffRequest request) {
        Role role;
        Long centreId;
        if (AccessScope.isSuperAdmin(me)) {
            role = request.role() == null ? Role.ADMIN : request.role();
            centreId = request.centreId();
            if (role != Role.ADMIN && role != Role.OPERATOR) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED", "Choose Centre admin or Staff.");
            }
            if (centreId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CENTRE_REQUIRED", "Choose the centre for this account.");
            }
        } else {
            role = Role.OPERATOR;          // a centre admin can only add staff for their own centre
            centreId = me.centreId();
        }
        Centre centre = centreRepository.findById(centreId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CENTRE_NOT_FOUND", "Centre not found."));

        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN",
                    "The username " + username + " is already used. Choose another one.");
        }
        User user = User.newStaff(request.name().trim(), role, username,
                passwordEncoder.encode(request.password()), centre);
        return StaffView.from(userRepository.save(user));
    }

    public StaffView update(AuthUser me, Long id, StaffUpdateRequest request) {
        User user = userRepository.findById(id)
                .filter(u -> canManage(me, u))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "Account not found."));
        user.setName(request.name().trim());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return StaffView.from(user);
    }

    private static boolean canManage(AuthUser me, User target) {
        if (AccessScope.isSuperAdmin(me)) {
            return target.getRole() == Role.ADMIN || target.getRole() == Role.OPERATOR;
        }
        return target.getRole() == Role.OPERATOR && target.getCentre() != null
                && target.getCentre().getId().equals(me.centreId());
    }
}
