package com.nineteen96.notificationservice.service;

import com.nineteen96.notificationservice.dto.CreateNotificationRequest;
import com.nineteen96.notificationservice.entity.Notification;
import com.nineteen96.notificationservice.entity.enums.NotificationStatus;
import com.nineteen96.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification();

        notification.setRecipient(request.getRecipient());
        notification.setTemplate(request.getTemplate());
        notification.setChannel(request.getChannel());

        notification.setStatus(NotificationStatus.PENDING);

        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());

        return notificationRepository.save(notification);
    }
}
