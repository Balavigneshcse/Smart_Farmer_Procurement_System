package com.cropcoders.procurement.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.cropcoders.procurement.TestProperties;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates(messages(),
            TestProperties.withJwtSecret("test-secret-that-is-long-enough-0123456789"));

    @Test
    void otpEmailShowsTheCodeAndValidity() {
        EmailMessage email = templates.otp("ravi@example.com", "en", "482913", 5);

        assertThat(email.to()).isEqualTo("ravi@example.com");
        assertThat(email.subject()).isEqualTo("482913 is your login OTP");
        assertThat(email.html()).contains("482913").contains("valid for 5 minutes").contains("lang=\"en\"");
        assertThat(email.text()).contains("482913").contains("ignore this email");
    }

    @Test
    void otpEmailInTamil() {
        EmailMessage email = templates.otp("ravi@example.com", "ta", "482913", 5);

        assertThat(email.subject()).contains("482913").contains("OTP");
        assertThat(email.html()).contains("lang=\"ta\"").contains("ஸ்மார்ட் விவசாயி கொள்முதல் அமைப்பு");
    }

    @Test
    void notificationHasGreetingAndLinkToTheBooking() {
        EmailMessage email = templates.notification("ravi@example.com", "hi", "Ravi", "बुकिंग पक्की - टोकन TNJ-001",
                "आपका स्लॉट बुक हो गया है।", "/farmer/booking.html?id=12");

        assertThat(email.html())
                .contains("नमस्ते Ravi,")
                .contains("href=\"http://localhost:8080/farmer/booking.html?id=12\"")
                .contains("मेरी बुकिंग खोलें");
        assertThat(email.text()).contains("http://localhost:8080/farmer/booking.html?id=12");
    }

    @Test
    void textFromUsersIsEscapedInHtml() {
        EmailMessage email = templates.notification("ravi@example.com", "en", "<script>alert(1)</script>",
                "Crop not accepted", "Reason: moisture > 17% & dirt", "/farmer/dashboard.html");

        assertThat(email.html())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .contains("moisture &gt; 17% &amp; dirt");
        // The plain-text copy is not HTML, so it keeps the text as typed.
        assertThat(email.text()).contains("Reason: moisture > 17% & dirt");
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
