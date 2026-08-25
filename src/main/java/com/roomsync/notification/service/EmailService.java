package com.roomsync.notification.service;

public interface EmailService {
    void sendEmail(String recipient, String subject, String body);
}
