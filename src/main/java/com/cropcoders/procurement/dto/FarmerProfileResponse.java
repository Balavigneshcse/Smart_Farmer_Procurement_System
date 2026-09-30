package com.cropcoders.procurement.dto;

import java.time.LocalDate;

import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.entity.User;

public record FarmerProfileResponse(
        Long id,
        String name,
        String email,
        String mobile,
        String address,
        String district,
        String aadhaarLast4,
        String language,
        LocalDate memberSince,
        String payoutMethod,
        String accountHolder,
        String accountNumberMasked,
        String ifsc,
        String bankName,
        String upiId) {

    public static FarmerProfileResponse from(FarmerProfile profile) {
        User user = profile.getUser();
        return new FarmerProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                profile.getAddress(),
                profile.getDistrict(),
                profile.getAadhaarLast4(),
                profile.getLanguage(),
                user.getCreatedAt().toLocalDate(),
                profile.getPayoutMethod(),
                profile.getAccountHolder(),
                masked(profile.getAccountNumber()),
                profile.getIfsc(),
                profile.getBankName(),
                profile.getUpiId());
    }

    /** "XXXXXXXX4321": only the last 4 digits of the account number go back to the browser. */
    private static String masked(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        int hidden = accountNumber.length() - 4;
        return "X".repeat(hidden) + accountNumber.substring(hidden);
    }
}
