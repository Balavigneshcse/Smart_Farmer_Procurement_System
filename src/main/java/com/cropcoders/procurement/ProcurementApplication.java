package com.cropcoders.procurement;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// We log users in ourselves (OTP / username + password with JWT),
// so Spring Boot's default in-memory user with a generated password is switched off.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync             // emails are sent in the background (EmailDispatcher)
public class ProcurementApplication {

    public static void main(String[] args) {
        // Slots, queues and timestamps are all Indian time, wherever the server runs.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(ProcurementApplication.class, args);
    }
}
