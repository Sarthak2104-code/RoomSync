package com.roomsync;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class RoomSyncApplication {

    @PostConstruct
    public void init() {
        // Ensure default JVM timezone is UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(RoomSyncApplication.class, args);
    }
}
