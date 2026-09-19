package com.nineteen96.notificationservice.event;

import com.nineteen96.notificationservice.entity.enums.Channel;

public class NotificationMessage {

    private Long notificationId;
    private String recipient;
    private Channel channel;
    private String template;

    public NotificationMessage() {}

    public NotificationMessage(Long notificationId, String recipient, Channel channel, String template) {
        this.notificationId = notificationId;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
