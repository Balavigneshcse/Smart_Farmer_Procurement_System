package com.cropcoders.procurement.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.FarmerProfileResponse;
import com.cropcoders.procurement.dto.PayoutRequest;
import com.cropcoders.procurement.dto.UpdateProfileRequest;
import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.FarmerProfileRepository;

/** A farmer viewing and editing their own details. */
@Service
@Transactional
public class FarmerService {

    private final FarmerProfileRepository profileRepository;

    public FarmerService(FarmerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public FarmerProfileResponse getProfile(Long farmerId) {
        return FarmerProfileResponse.from(find(farmerId));
    }

    public FarmerProfileResponse updateProfile(Long farmerId, UpdateProfileRequest request) {
        FarmerProfile profile = find(farmerId);
        profile.getUser().setName(request.name().trim());
        profile.setAddress(request.address().trim());
        profile.setDistrict(request.district().trim());
        profile.setLanguage(request.language());
        return FarmerProfileResponse.from(profile);
    }

    /** Changes the language of the farmer's pages and emails. */
    public FarmerProfileResponse updateLanguage(Long farmerId, String language) {
        FarmerProfile profile = find(farmerId);
        profile.setLanguage(language);
        return FarmerProfileResponse.from(profile);
    }

    /** Changes where the farmer is paid (bank account or UPI). */
    public FarmerProfileResponse updatePayout(Long farmerId, PayoutRequest request) {
        FarmerProfile profile = find(farmerId);
        applyPayout(profile, request.checked());
        return FarmerProfileResponse.from(profile);
    }

    /** payout must already be checked (PayoutRequest.checked()). */
    static void applyPayout(FarmerProfile profile, PayoutRequest payout) {
        if (payout.isBank()) {
            profile.payToBank(payout.accountHolder(), payout.accountNumber(), payout.ifsc(), payout.bankName());
        } else {
            profile.payToUpi(payout.upiId());
        }
    }

    private FarmerProfile find(Long farmerId) {
        return profileRepository.findById(farmerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Farmer profile not found."));
    }
}
