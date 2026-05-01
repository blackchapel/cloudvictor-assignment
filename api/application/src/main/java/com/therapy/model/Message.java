package com.therapy.model;

public class Message {
    private String messageId;
    private String clientId;
    private String therapistId;
    private String senderType;
    private String senderId;
    private String content;
    private String sentAt;
    private String editedAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getTherapistId() { return therapistId; }
    public void setTherapistId(String therapistId) { this.therapistId = therapistId; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }

    public String getEditedAt() { return editedAt; }
    public void setEditedAt(String editedAt) { this.editedAt = editedAt; }
}
