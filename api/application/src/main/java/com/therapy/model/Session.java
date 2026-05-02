package com.therapy.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Session {

    public static final int PRIVATE_NOTES_MAX_CHARS = 10_000;
    public static final int SHARED_NOTES_MAX_CHARS  = 5_000;
    public static final int TITLE_MAX_CHARS         = 200;
    public static final int DESCRIPTION_MAX_CHARS   = 1_000;
    public static final int MAX_DURATION_MINUTES    = 240;

    private String sessionId;
    private String therapistId;
    private String title;
    private String description;
    private String scheduledAt;
    private Integer durationMinutes;
    private Boolean isAvailable;
    private String status;
    private String startedAt;
    private String endedAt;
    private String confirmedAppointmentId;
    private Integer pendingCount;
    private String privateNotes;
    private String sharedNotes;
    private String createdAt;
    private String updatedAt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTherapistId() { return therapistId; }
    public void setTherapistId(String therapistId) { this.therapistId = therapistId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }

    public String getConfirmedAppointmentId() { return confirmedAppointmentId; }
    public void setConfirmedAppointmentId(String confirmedAppointmentId) { this.confirmedAppointmentId = confirmedAppointmentId; }

    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }

    public String getPrivateNotes() { return privateNotes; }
    public void setPrivateNotes(String privateNotes) { this.privateNotes = privateNotes; }

    public String getSharedNotes() { return sharedNotes; }
    public void setSharedNotes(String sharedNotes) { this.sharedNotes = sharedNotes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
