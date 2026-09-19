package com.nineteen96.notificationservice.app;

import com.nineteen96.notificationservice.config.RabbitMQConfig;
import com.nineteen96.notificationservice.event.NotificationMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consume(NotificationMessage message) {
        System.out.println("Processing notification " + message.getNotificationId());
        System.out.println("Sending " + message.getChannel() + " to " + message.getRecipient());

        System.out.println("Notification sent!");
    }
}
