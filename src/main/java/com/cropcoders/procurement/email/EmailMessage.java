package com.cropcoders.procurement.email;

/**
 * One email ready to send: an HTML body plus a plain-text copy for simple mail apps.
 * qrPng is an optional picture (the booking QR code); the HTML shows it with src="cid:booking-qr".
 */
public record EmailMessage(String to, String subject, String html, String text, byte[] qrPng) {

    /** The name the HTML uses to point at the attached QR picture. */
    public static final String QR_CONTENT_ID = "booking-qr";
}
