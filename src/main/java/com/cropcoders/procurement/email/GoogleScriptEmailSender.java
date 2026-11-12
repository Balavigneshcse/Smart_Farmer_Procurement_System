package com.cropcoders.procurement.email;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.cropcoders.procurement.config.AppProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends email from your own Gmail through a Google Apps Script web app (EMAIL_PROVIDER=google-script).
 * Used on Render, whose free plan blocks the SMTP ports; the script is reached over normal HTTPS.
 * The script is deploy/google-apps-script/Code.gs; Gmail allows about 100 emails a day this way.
 *
 * Needs GOOGLE_SCRIPT_URL (the web app's /exec address) and GOOGLE_SCRIPT_SECRET (the same value as the
 * script's SECRET property). The booking QR travels with the email as an inline picture (cid:booking-qr).
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "google-script")
public class GoogleScriptEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(GoogleScriptEmailSender.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)   // Google answers the POST with 302; we follow it ourselves with a GET
            .build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final URI scriptUrl;
    private final String secret;
    private final String fromName;

    public GoogleScriptEmailSender(AppProperties properties,
                                   @Value("${GOOGLE_SCRIPT_URL:}") String scriptUrl,
                                   @Value("${GOOGLE_SCRIPT_SECRET:}") String secret) {
        if (scriptUrl.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=google-script needs GOOGLE_SCRIPT_URL and GOOGLE_SCRIPT_SECRET");
        }
        this.scriptUrl = URI.create(scriptUrl.trim());
        this.secret = secret.trim();
        this.fromName = properties.email().fromName();
    }

    @Override
    public String send(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secret", secret);
        body.put("to", message.to());
        body.put("subject", message.subject());
        body.put("text", message.text());
        body.put("html", message.html());
        body.put("fromName", fromName);
        if (message.qrPng() != null) {
            body.put("inlineImages", Map.of(EmailMessage.QR_CONTENT_ID, Base64.getEncoder().encodeToString(message.qrPng())));
        }
        HttpRequest post = HttpRequest.newBuilder(scriptUrl)
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = postOnce(post);
        // Google answers with 302 to the place where the script's answer can be read
        if (response.statusCode() == 302 || response.statusCode() == 303) {
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new EmailSendException("Google script answered 302 without a Location", null));
            response = get(URI.create(location));
        }
        if (response.statusCode() != 200) {
            throw new EmailSendException("Google script answered HTTP " + response.statusCode(), null);
        }
        // Some Google error pages also come with 200: only {"ok": true} means the email was sent
        JsonNode answer;
        try {
            answer = json.readTree(response.body());
        } catch (RuntimeException e) {
            throw new EmailSendException("Google script did not answer with JSON (is the deployment's access set to Anyone?)", e);
        }
        if (!answer.path("ok").asBoolean(false)) {
            String error = answer.path("error").asString("unknown");
            log.warn("Google script refused the email: {}", error);
            throw new EmailSendException("Google script refused the email: " + error, null);
        }
        return "google-script";
    }

    /** One POST; tried a second time only when the connection itself failed, so nobody gets the email twice. */
    private HttpResponse<String> postOnce(HttpRequest post) {
        try {
            return http.send(post, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new EmailSendException("Google script did not answer in time", e);
        } catch (IOException first) {
            try {
                return http.send(post, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new EmailSendException("Could not reach the Google script: " + e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Interrupted while sending the email", e);
        }
    }

    private HttpResponse<String> get(URI location) {
        try {
            return http.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(40)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmailSendException("Could not read the Google script's answer: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Interrupted while sending the email", e);
        }
    }
}
