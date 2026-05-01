package com.therapy.model;

public class Mapping {
    private String mappingId;
    private String clientId;
    private String therapistId;
    private String mappingStatus;
    private String journalAccessStatus;
    private String initiatedBy;
    private String createdAt;
    private String updatedAt;

    public String getMappingId() { return mappingId; }
    public void setMappingId(String mappingId) { this.mappingId = mappingId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getTherapistId() { return therapistId; }
    public void setTherapistId(String therapistId) { this.therapistId = therapistId; }

    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }

    public String getJournalAccessStatus() { return journalAccessStatus; }
    public void setJournalAccessStatus(String journalAccessStatus) { this.journalAccessStatus = journalAccessStatus; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
