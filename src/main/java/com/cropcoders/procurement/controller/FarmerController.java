package com.cropcoders.procurement.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.FarmerProfileResponse;
import com.cropcoders.procurement.dto.PayoutRequest;
import com.cropcoders.procurement.dto.UpdateLanguageRequest;
import com.cropcoders.procurement.dto.UpdateProfileRequest;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.FarmerService;

import jakarta.validation.Valid;

/** The logged-in farmer's own profile. Only role FARMER can call these (see SecurityConfig). */
@RestController
@RequestMapping("/api/farmers/me")
public class FarmerController {

    private final FarmerService farmerService;

    public FarmerController(FarmerService farmerService) {
        this.farmerService = farmerService;
    }

    @GetMapping
    public FarmerProfileResponse profile(@AuthenticationPrincipal AuthUser farmer) {
        return farmerService.getProfile(farmer.id());
    }

    @PutMapping
    public FarmerProfileResponse updateProfile(@AuthenticationPrincipal AuthUser farmer,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return farmerService.updateProfile(farmer.id(), request);
    }

    @PutMapping("/payout")
    public FarmerProfileResponse updatePayout(@AuthenticationPrincipal AuthUser farmer,
                                              @Valid @RequestBody PayoutRequest request) {
        return farmerService.updatePayout(farmer.id(), request);
    }

    @PutMapping("/language")
    public FarmerProfileResponse updateLanguage(@AuthenticationPrincipal AuthUser farmer,
                                                @Valid @RequestBody UpdateLanguageRequest request) {
        return farmerService.updateLanguage(farmer.id(), request.language());
    }
}
