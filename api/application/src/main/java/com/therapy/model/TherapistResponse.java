package com.therapy.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TherapistResponse {

    private String therapistId;
    private String email;
    private String name;
    private String licenseNumber;
    private String specialization;
    private String bio;
    private String qualification;
    private Integer yearsOfExperience;
    private String createdAt;
    private String updatedAt;

    public static TherapistResponse from(User user) {
        TherapistResponse r = new TherapistResponse();
        r.therapistId       = user.getUserId();
        r.email             = user.getEmail();
        r.name              = user.getName();
        r.licenseNumber     = user.getLicenseNumber();
        r.specialization    = user.getSpecialization();
        r.bio               = user.getBio();
        r.qualification     = user.getQualification();
        r.yearsOfExperience = user.getYearsOfExperience();
        r.createdAt         = user.getCreatedAt();
        r.updatedAt         = user.getUpdatedAt();
        return r;
    }

    public String getTherapistId()        { return therapistId; }
    public String getEmail()              { return email; }
    public String getName()               { return name; }
    public String getLicenseNumber()      { return licenseNumber; }
    public String getSpecialization()     { return specialization; }
    public String getBio()                { return bio; }
    public String getQualification()      { return qualification; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public String getCreatedAt()          { return createdAt; }
    public String getUpdatedAt()          { return updatedAt; }
}
