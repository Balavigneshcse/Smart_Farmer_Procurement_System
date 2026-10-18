package com.cropcoders.procurement.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.AuthResponse;
import com.cropcoders.procurement.dto.LoginRequest;
import com.cropcoders.procurement.dto.PayoutRequest;
import com.cropcoders.procurement.dto.RegisterRequest;
import com.cropcoders.procurement.dto.SendOtpRequest;
import com.cropcoders.procurement.dto.SendOtpResponse;
import com.cropcoders.procurement.dto.UserResponse;
import com.cropcoders.procurement.dto.VerifyOtpRequest;
import com.cropcoders.procurement.dto.VerifyOtpResponse;
import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.FarmerProfileRepository;
import com.cropcoders.procurement.repository.UserRepository;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.security.JwtService;

/**
 * Farmer login:  send OTP to email -> verify OTP -> (new farmer) register -> login token.
 * Staff login:   username + password -> login token.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final FarmerProfileRepository profileRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository, FarmerProfileRepository profileRepository, OtpService otpService,
                       JwtService jwtService, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public SendOtpResponse sendOtp(SendOtpRequest request) {
        // A registered farmer gets the OTP in their saved language; a new one in the page language.
        String email = normalise(request.email());
        if (DemoDataService.isDemoEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEMO_ACCOUNT", "Demo farmers cannot log in.");
        }
        String language = userRepository.findByEmail(email)
                .flatMap(user -> profileRepository.findById(user.getId()))
                .map(FarmerProfile::getLanguage)
                .orElse(request.language() == null ? "en" : request.language());

        int resendAfter = otpService.sendOtp(email, language);
        return new SendOtpResponse(resendAfter, properties.otp().validMinutes());
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String email = normalise(request.email());
        otpService.verifyOtp(email, request.otp());

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            return VerifyOtpResponse.needsRegistration(jwtService.createRegistrationToken(email));
        }
        User user = existing.get();
        ensureActive(user);
        return VerifyOtpResponse.loggedIn(jwtService.createLoginToken(AuthUser.from(user)), farmerResponse(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = jwtService.parseRegistrationToken(request.registrationToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REGISTRATION_EXPIRED",
                        "Your verification has expired. Please verify your email again."));
        PayoutRequest payout = request.payout().checked();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED",
                    "This email address is already registered. Please log in.");
        }

        User user = userRepository.save(User.newFarmer(request.name().trim(), email, request.mobile()));
        FarmerProfile profile = new FarmerProfile(user, request.address().trim(), request.district().trim(),
                request.aadhaarLast4(), request.language());
        FarmerService.applyPayout(profile, payout);
        profileRepository.save(profile);

        return new AuthResponse(jwtService.createLoginToken(AuthUser.from(user)),
                UserResponse.farmer(user, request.language()));
    }

    /**
     * Staff login. After app.staff-login.max-attempts wrong passwords in a row the account is locked for
     * lock-minutes, so a password cannot be guessed by trying many. The account row is locked while the
     * password is checked, so wrong passwords sent at the same moment are all counted.
     * noRollbackFor: a wrong password must still be counted even though we throw an error (like the OTP).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse staffLogin(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.findForLoginByUsername(request.username().trim())
                .filter(u -> u.getRole() != Role.FARMER)
                .orElseThrow(AuthService::badCredentials);
        if (user.isLoginLocked(now)) {
            long minutes = Math.max(1, (Duration.between(now, user.getLockedUntil()).toSeconds() + 59) / 60);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "Too many wrong passwords. Please try again in " + minutes + " minutes, or ask your admin "
                            + "to set a new password.", Map.of("minutes", minutes));
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            AppProperties.StaffLogin rules = properties.staffLogin();
            user.recordFailedLogin(now, rules.maxAttempts(), rules.lockMinutes());
            throw badCredentials();
        }
        user.clearFailedLogins();
        ensureActive(user);
        return new AuthResponse(jwtService.createLoginToken(AuthUser.from(user)), UserResponse.staff(user));
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Wrong username or password.");
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in again."));
        ensureActive(user);
        return user.getRole() == Role.FARMER ? farmerResponse(user) : UserResponse.staff(user);
    }

    private UserResponse farmerResponse(User user) {
        String language = profileRepository.findById(user.getId()).map(FarmerProfile::getLanguage).orElse("en");
        return UserResponse.farmer(user, language);
    }

    /** Emails are stored in lower case, so "Ravi@Gmail.com" and "ravi@gmail.com" are the same farmer. */
    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void ensureActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Your account is disabled. Please contact the procurement centre.");
        }
    }
}
