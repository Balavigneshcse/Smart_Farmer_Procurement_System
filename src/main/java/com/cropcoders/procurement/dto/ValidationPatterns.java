package com.cropcoders.procurement.dto;

/** Regular expressions shared by request validations. */
public final class ValidationPatterns {

    /** Indian mobile number: 10 digits starting with 6, 7, 8 or 9. */
    public static final String MOBILE = "^[6-9]\\d{9}$";
    /** A simple email check (name@domain.tld); the OTP email proves the address really works. */
    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String OTP ="^\\d{6}$";
    public static final String AADHAAR_LAST4 = "^\\d{4}$";
    public static final String LANGUAGE = "^(en|ta|hi)$";

    private ValidationPatterns() {
    }
}
