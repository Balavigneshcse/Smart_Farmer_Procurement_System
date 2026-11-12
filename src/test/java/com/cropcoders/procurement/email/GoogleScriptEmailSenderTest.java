package com.cropcoders.procurement.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.cropcoders.procurement.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Talks to a fake Google script that behaves like the real one: 302 on the POST, the answer on the GET. */
class GoogleScriptEmailSenderTest {

    private HttpServer server;
    private final AtomicReference<String> posted = new AtomicReference<>();

    private String start(String answer) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/exec", exchange -> {
            posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/answer");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/answer", exchange -> reply(exchange, answer));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/exec";
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static GoogleScriptEmailSender sender(String url) {
        AppProperties.Email email = new AppProperties.Email("google-script", "", "Smart Farmer", 50, "https://example.org");
        return new GoogleScriptEmailSender(new AppProperties("x", null, null, null, email, null, null, null), url, "s".repeat(40));
    }

    private static EmailMessage message() {
        return new EmailMessage("farmer@example.org", "Booking confirmed", "<img src=\"cid:booking-qr\">", "text", new byte[] {1, 2, 3}, null);
    }

    @Test
    void sentOnlyWhenTheScriptSaysOkAndTheQrTravelsInline() throws IOException {
        String url = start("{\"ok\":true}");
        assertThat(sender(url).send(message())).isEqualTo("google-script");
        assertThat(posted.get()).contains("\"to\":\"farmer@example.org\"").contains("\"booking-qr\":\"AQID\"");
    }

    @Test
    void aRefusalIsAnError() throws IOException {
        String url = start("{\"ok\":false,\"error\":\"unauthorized\"}");
        assertThatThrownBy(() -> sender(url).send(message())).isInstanceOf(EmailSendException.class).hasMessageContaining("unauthorized");
    }

    @Test
    void aGooglePageWithStatus200IsNotSuccess() throws IOException {
        String url = start("<html>Sign in</html>");
        assertThatThrownBy(() -> sender(url).send(message())).isInstanceOf(EmailSendException.class);
    }
}
