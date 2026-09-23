package com.cropcoders.procurement.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Talks to Razorpay (test mode only).
 *
 * Payment flow:
 *  1. createOrder()  - our server asks Razorpay for an order with the exact amount (server decides the amount).
 *  2. The admin's browser opens Razorpay Checkout for that order and pays (test UPI: success@razorpay).
 *  3. Checkout returns order id + payment id + signature to the browser, which sends them to us.
 *  4. isValidSignature() - we recompute HMAC-SHA256(orderId + "|" + paymentId, keySecret).
 *     Only Razorpay and our server know the secret, so a matching signature proves the payment is real.
 */
@Component
public class RazorpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final String TEST_KEY_PREFIX = "rzp_test_";

    private final AppProperties.Razorpay config;
    private volatile RestClient http;     // created on first use, only when payments are configured

    public RazorpayGateway(AppProperties properties) {
        this.config = properties.razorpay();
    }

    private RestClient http() {
        if (http == null) {
            synchronized (this) {
                if (http == null) {
                    http = RestClient.builder()
                            .baseUrl(config.apiUrl())
                            .defaultHeaders(headers -> headers.setBasicAuth(config.keyId(), config.keySecret()))
                            .build();
                }
            }
        }
        return http;
    }

    /** True only for test keys, so this project can never move real money. */
    public boolean isConfigured() {
        return notBlank(config.keyId()) && notBlank(config.keySecret()) && config.keyId().startsWith(TEST_KEY_PREFIX);
    }

    public String keyId() {
        return config.keyId();
    }

    @EventListener(ApplicationReadyEvent.class)
    void logStatus() {
        if (isConfigured()) {
            log.info("Razorpay: test mode, key {}...", config.keyId().substring(0, Math.min(14, config.keyId().length())));
        } else if (notBlank(config.keyId()) && !config.keyId().startsWith(TEST_KEY_PREFIX)) {
            log.error("Razorpay: the key is not a TEST key (rzp_test_...). Payments are disabled.");
        } else {
            log.warn("Razorpay: RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET not set. Payments are disabled.");
        }
    }

    /** Creates a Razorpay order and returns its id (order_...). */
    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        requireConfigured();
        Map<String, Object> body = Map.of(
                "amount", amountPaise,
                "currency", "INR",
                "receipt", receipt,
                "notes", notes);
        try {
            OrderResponse order = http().post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OrderResponse.class);
            if (order == null || order.id() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ERROR", "Razorpay did not return an order.");
            }
            return order.id();
        } catch (RestClientResponseException e) {
            log.warn("Razorpay order failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            String hint = e.getStatusCode().value() == 401 ? " Check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET." : "";
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ERROR",
                    "Razorpay refused the request (" + e.getStatusCode().value() + ")." + hint);
        } catch (RestClientException e) {
            log.warn("Razorpay unreachable: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ERROR", "Could not reach Razorpay. Try again.");
        }
    }

    /** Checks the signature Razorpay Checkout returned for a successful payment. */
    public boolean isValidSignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        return signatureMatches(orderId, paymentId, signature, config.keySecret());
    }

    /** HMAC-SHA256 of "orderId|paymentId" with the key secret, compared in constant time. */
    static boolean signatureMatches(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String expected = hmacSha256Hex(orderId + "|" + paymentId, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "Razorpay test keys are not set. Add RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to .env and restart.");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderResponse(String id, String status, Long amount) {
    }
}
