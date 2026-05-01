package com.therapy.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientResponse {

    private String clientId;
    private String email;
    private String name;
    private String createdAt;
    private String updatedAt;

    public static ClientResponse from(User user) {
        ClientResponse r = new ClientResponse();
        r.clientId   = user.getUserId();
        r.email      = user.getEmail();
        r.name       = user.getName();
        r.createdAt  = user.getCreatedAt();
        r.updatedAt  = user.getUpdatedAt();
        return r;
    }

    public String getClientId()    { return clientId; }
    public String getEmail()       { return email; }
    public String getName()        { return name; }
    public String getCreatedAt()   { return createdAt; }
    public String getUpdatedAt()   { return updatedAt; }
}
