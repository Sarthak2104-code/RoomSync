package com.roomsync.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SimpleEmailService implements EmailService {

    @Override
    public void sendEmail(String recipient, String subject, String body) {
        log.info("Dispatching email to recipient: '{}' | Subject: '{}' | Content: {}", recipient, subject, body);
    }
}
