package com.cropcoders.procurement.dto;

import org.springframework.http.HttpStatus;

import com.cropcoders.procurement.exception.ApiException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where the farmer wants to be paid: a bank account or a UPI ID.
 *   BANK: account holder, account number (9 to 18 digits), IFSC (e.g. SBIN0001234) and bank name.
 *   UPI:  the UPI ID (e.g. ravi@okaxis).
 */
public record PayoutRequest(
        @NotBlank @Pattern(regexp = "^(BANK|UPI)$", message = "Choose bank account or UPI") String method,
        @Size(max = 100) String accountHolder,
        @Size(max = 18) String accountNumber,
        @Size(max = 11) String ifsc,
        @Size(max = 100) String bankName,
        @Size(max = 100) String upiId) {

    private static final String ACCOUNT_NUMBER = "^\\d{9,18}$";
    /** 4 letters (the bank), a zero, then 6 letters or digits (the branch). */
    private static final String IFSC = "^[A-Z]{4}0[A-Z0-9]{6}$";
    private static final String UPI_ID = "^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$";

    public boolean isBank() {
        return "BANK".equals(method);
    }

    /** The cleaned-up details, or an error that says what is wrong (the form checks the same rules first). */
    public PayoutRequest checked() {
        if (isBank()) {
            String holder = trim(accountHolder);
            String number = trim(accountNumber).replace(" ", "");
            String code = trim(ifsc).toUpperCase();
            String bank = trim(bankName);
            if (holder.length() < 2 || bank.length() < 2 || !number.matches(ACCOUNT_NUMBER) || !code.matches(IFSC)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_BANK_INVALID",
                        "Check the bank details: account holder name, account number (9 to 18 digits), "
                                + "IFSC code (like SBIN0001234) and bank name.");
            }
            return new PayoutRequest("BANK", holder, number, code, bank, null);
        }
        String upi = trim(upiId);
        if (!upi.matches(UPI_ID)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_UPI_INVALID",
                    "Enter a valid UPI ID, for example name@okaxis.");
        }
        return new PayoutRequest("UPI", null, null, null, null, upi);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
