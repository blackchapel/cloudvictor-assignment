package com.therapy.repository;

import com.therapy.model.User;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class UserRepository {

    private static final String TABLE = System.getenv("USER_TABLE_NAME");
    private final DynamoDbClient ddb;

    public UserRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void create(User user) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId",       s(user.getUserId()));
        item.put("userType",     s(user.getUserType()));
        item.put("email",        s(user.getEmail()));
        item.put("passwordHash", s(user.getPasswordHash()));
        item.put("name",         s(user.getName()));
        item.put("createdAt",    s(user.getCreatedAt()));
        item.put("updatedAt",    s(user.getUpdatedAt()));

        if (user.getLicenseNumber()  != null) item.put("licenseNumber",  s(user.getLicenseNumber()));
        if (user.getSpecialization() != null) item.put("specialization", s(user.getSpecialization()));
        if (user.getBio()            != null) item.put("bio",            s(user.getBio()));
        if (user.getQualification()  != null) item.put("qualification",  s(user.getQualification()));
        if (user.getYearsOfExperience() != null)
            item.put("yearsOfExperience", n(user.getYearsOfExperience()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(userId)")
                .build());
    }

    /** Login lookup — queries GSI_EmailIndex which projects passwordHash. */
    public Optional<User> findByEmail(String email) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_EmailIndex")
                .keyConditionExpression("email = :email")
                .expressionAttributeValues(Map.of(":email", s(email)))
                .limit(1)
                .build());
        if (resp.count() == 0) return Optional.empty();
        return Optional.of(itemToUser(resp.items().get(0)));
    }

    /** Email uniqueness check — only checks existence, no data needed. */
    public boolean existsByEmail(String email) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_EmailIndex")
                .keyConditionExpression("email = :email")
                .expressionAttributeValues(Map.of(":email", s(email)))
                .limit(1)
                .projectionExpression("email")
                .build());
        return resp.count() > 0;
    }

    private User itemToUser(Map<String, AttributeValue> item) {
        User u = new User();
        u.setUserId(str(item, "userId"));
        u.setUserType(str(item, "userType"));
        u.setEmail(str(item, "email"));
        u.setPasswordHash(str(item, "passwordHash"));
        u.setName(str(item, "name"));
        u.setLicenseNumber(str(item, "licenseNumber"));
        u.setSpecialization(str(item, "specialization"));
        u.setBio(str(item, "bio"));
        u.setQualification(str(item, "qualification"));
        if (item.containsKey("yearsOfExperience"))
            u.setYearsOfExperience(Integer.parseInt(item.get("yearsOfExperience").n()));
        u.setCreatedAt(str(item, "createdAt"));
        u.setUpdatedAt(str(item, "updatedAt"));
        return u;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static AttributeValue n(int v) {
        return AttributeValue.builder().n(String.valueOf(v)).build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }
}
