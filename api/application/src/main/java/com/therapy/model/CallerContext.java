package com.therapy.model;

public class CallerContext {
    private final String userId;
    private final String userType;

    public CallerContext(String userId, String userType) {
        this.userId = userId;
        this.userType = userType;
    }

    public String getUserId() { return userId; }
    public String getUserType() { return userType; }

    public boolean isClient() { return "CLIENT".equals(userType); }
    public boolean isTherapist() { return "THERAPIST".equals(userType); }
}
