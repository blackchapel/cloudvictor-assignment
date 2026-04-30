package com.therapy.model;

public class User {

    private String userId;
    private String userType;
    private String email;
    private String passwordHash;
    private String name;
    private String phoneNumber;

    // Therapist-only fields
    private String licenseNumber;
    private String specialization;
    private String bio;
    private String qualification;
    private Integer yearsOfExperience;

    private String createdAt;
    private String updatedAt;

    public String getUserId()           { return userId; }
    public void setUserId(String v)     { this.userId = v; }

    public String getUserType()         { return userType; }
    public void setUserType(String v)   { this.userType = v; }

    public String getEmail()            { return email; }
    public void setEmail(String v)      { this.email = v; }

    public String getPasswordHash()     { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }

    public String getName()             { return name; }
    public void setName(String v)       { this.name = v; }

    public String getLicenseNumber()    { return licenseNumber; }
    public void setLicenseNumber(String v) { this.licenseNumber = v; }

    public String getSpecialization()   { return specialization; }
    public void setSpecialization(String v) { this.specialization = v; }

    public String getBio()              { return bio; }
    public void setBio(String v)        { this.bio = v; }

    public String getQualification()    { return qualification; }
    public void setQualification(String v) { this.qualification = v; }

    public Integer getYearsOfExperience()      { return yearsOfExperience; }
    public void setYearsOfExperience(Integer v) { this.yearsOfExperience = v; }

    public String getCreatedAt()        { return createdAt; }
    public void setCreatedAt(String v)  { this.createdAt = v; }

    public String getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(String v)  { this.updatedAt = v; }
}
