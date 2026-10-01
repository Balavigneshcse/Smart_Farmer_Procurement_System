package com.cropcoders.procurement.email;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.cropcoders.procurement.config.AppProperties;

/**
 * Sends email through Brevo's HTTPS API (EMAIL_PROVIDER=brevo). Used on Render, whose free plan
 * blocks the SMTP ports that Gmail needs. Free plan: 300 emails a day.
 *
 * Setup: create a Brevo account, add and verify the sender address (MAIL_FROM), and create an
 * API key (BREVO_API_KEY) under SMTP & API.
 *
 * Brevo's API has no inline pictures, so the booking QR is shown from a signed link to this app
 * (the email's qrUrl) instead of being sent inside the email.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final String API = "https://api.brevo.com/v3";

    private final RestClient http;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(AppProperties properties, @Value("${BREVO_API_KEY:}") String apiKey) {
        if (apiKey.isBlank() || properties.email().from() == null || properties.email().from().isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=brevo needs BREVO_API_KEY and MAIL_FROM (a verified Brevo sender)");
        }
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(API)
                .requestFactory(timeouts)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.fromEmail = properties.email().from();
        this.fromName = properties.email().fromName();
    }

    @Override
    public String send(EmailMessage message) {
        String html = message.html();
        if (message.qrUrl() != null) {
            html = html.replace("cid:" + EmailMessage.QR_CONTENT_ID, message.qrUrl());
        }
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", message.to())),
                "subject", message.subject(),
                "htmlContent", html,
                "textContent", message.text());
        try {
            Map<?, ?> result = http.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object id = result == null ? null : result.get("messageId");
            return id == null ? "brevo" : id.toString();
        } catch (RestClientResponseException e) {
            log.warn("Brevo refused the email: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new EmailSendException("Brevo refused the email (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new EmailSendException("Could not reach Brevo: " + e.getMessage(), e);
        }
    }
}
