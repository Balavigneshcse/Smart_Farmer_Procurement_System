package com.cropcoders.procurement.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.cropcoders.procurement.config.AppProperties;

/**
 * Signs links to booking QR pictures (/api/qr/{id}.png?sig=...) with HMAC-SHA256 and the JWT secret.
 * Only links made by the server work, so the booking number in a link cannot simply be changed.
 */
@Component
public class QrLinkSigner {

    private final SecretKeySpec key;
    private final String appUrl;

    public QrLinkSigner(AppProperties properties) {
        this.key = new SecretKeySpec(("qr:" + properties.jwt().secret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String url = properties.email().appUrl();
        this.appUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The full link to the booking's QR picture, for emails. */
    public String url(Long bookingId) {
        return appUrl + "/api/qr/" + bookingId + ".png?sig=" + sign(bookingId);
    }

    public boolean isValid(Long bookingId, String signature) {
        return signature != null && MessageDigest.isEqual(
                sign(bookingId).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(Long bookingId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(String.valueOf(bookingId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
