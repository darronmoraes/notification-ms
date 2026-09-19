package com.nineteen96.notificationservice.service;

import com.nineteen96.notificationservice.app.NotificationProducer;
import com.nineteen96.notificationservice.dto.CreateNotificationRequest;
import com.nineteen96.notificationservice.event.NotificationMessage;
import com.nineteen96.notificationservice.entity.Notification;
import com.nineteen96.notificationservice.entity.enums.NotificationStatus;
import com.nineteen96.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private final NotificationProducer notificationProducer;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationProducer notificationProducer) {
        this.notificationRepository = notificationRepository;
        this.notificationProducer = notificationProducer;
    }

    public Notification createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification();

        notification.setRecipient(request.getRecipient());
        notification.setTemplate(request.getTemplate());
        notification.setChannel(request.getChannel());

        notification.setStatus(NotificationStatus.PENDING);

        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);

        NotificationMessage message =
                new NotificationMessage(
                        saved.getId(),
                        saved.getRecipient(),
                        saved.getChannel(),
                        saved.getTemplate()
                );

        notificationProducer.send(message);
        return saved;
    }
}
