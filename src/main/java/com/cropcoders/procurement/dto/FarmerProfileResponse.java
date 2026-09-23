package com.cropcoders.procurement.dto;

import java.time.LocalDate;

import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.entity.User;

public record FarmerProfileResponse(
        Long id,
        String name,
        String mobile,
        String address,
        String district,
        String aadhaarLast4,
        String language,
        LocalDate memberSince) {

    public static FarmerProfileResponse from(FarmerProfile profile) {
        User user = profile.getUser();
        return new FarmerProfileResponse(
                user.getId(),
                user.getName(),
                user.getMobile(),
                profile.getAddress(),
                profile.getDistrict(),
                profile.getAadhaarLast4(),
                profile.getLanguage(),
                user.getCreatedAt().toLocalDate());
    }
}
