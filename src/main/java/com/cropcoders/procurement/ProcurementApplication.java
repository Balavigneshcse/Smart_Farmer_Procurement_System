package com.cropcoders.procurement;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProcurementApplication {

    public static void main(String[] args) {
        // Slots, queues and timestamps are all Indian time, wherever the server runs.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(ProcurementApplication.class, args);
    }
}
