package com.nineteen96.notificationservice.controller;

import com.nineteen96.notificationservice.dto.CreateNotificationRequest;
import com.nineteen96.notificationservice.entity.Notification;
import com.nineteen96.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<Notification> create(@Valid @RequestBody CreateNotificationRequest request) {
        Notification notification = notificationService.createNotification(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notification);
    }
}
