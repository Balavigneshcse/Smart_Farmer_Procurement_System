package com.cropcoders.procurement.email;

/** One email ready to send: an HTML body plus a plain-text copy for simple mail apps. */
public record EmailMessage(String to, String subject, String html, String text) {
}
