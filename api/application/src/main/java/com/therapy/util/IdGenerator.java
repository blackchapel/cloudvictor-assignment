package com.therapy.util;

import java.util.UUID;

public class IdGenerator {

    public static String sessionId() {
        return "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String mappingId() {
        return "map_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String messageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String clientId() {
        return "cli_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String therapistId() {
        return "thr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String appointmentId() {
        return "apt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
