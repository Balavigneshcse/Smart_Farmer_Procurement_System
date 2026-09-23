package com.cropcoders.procurement.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.AuthResponse;
import com.cropcoders.procurement.dto.LoginRequest;
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
 * Farmer login:  send OTP -> verify OTP -> (new farmer) register -> login token.
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
        String language = userRepository.findByMobile(request.mobile())
                .flatMap(user -> profileRepository.findById(user.getId()))
                .map(FarmerProfile::getLanguage)
                .orElse(request.language() == null ? "en" : request.language());

        int resendAfter = otpService.sendOtp(request.mobile(), language);
        return new SendOtpResponse(resendAfter, properties.otp().validMinutes());
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verifyOtp(request.mobile(), request.otp());

        Optional<User> existing = userRepository.findByMobile(request.mobile());
        if (existing.isEmpty()) {
            return VerifyOtpResponse.needsRegistration(jwtService.createRegistrationToken(request.mobile()));
        }
        User user = existing.get();
        ensureActive(user);
        return VerifyOtpResponse.loggedIn(jwtService.createLoginToken(AuthUser.from(user)), farmerResponse(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String mobile = jwtService.parseRegistrationToken(request.registrationToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REGISTRATION_EXPIRED",
                        "Your verification has expired. Please verify your mobile again."));
        if (userRepository.existsByMobile(mobile)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED",
                    "This mobile number is already registered. Please log in.");
        }

        User user = userRepository.save(User.newFarmer(request.name().trim(), mobile));
        profileRepository.save(new FarmerProfile(user, request.address().trim(), request.district().trim(),
                request.aadhaarLast4(), request.language()));

        return new AuthResponse(jwtService.createLoginToken(AuthUser.from(user)),
                UserResponse.farmer(user, request.language()));
    }

    @Transactional(readOnly = true)
    public AuthResponse staffLogin(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .filter(u -> u.getRole() != Role.FARMER)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Wrong username or password."));
        ensureActive(user);
        return new AuthResponse(jwtService.createLoginToken(AuthUser.from(user)), UserResponse.staff(user));
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

    private static void ensureActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Your account is disabled. Please contact the procurement centre.");
        }
    }
}
