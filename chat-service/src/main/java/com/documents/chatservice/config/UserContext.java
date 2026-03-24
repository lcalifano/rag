package com.documents.chatservice.config;

public class UserContext {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLES = new ThreadLocal<>();

    public static void set(String userId, String username, String roles) {
        USER_ID.set(userId);
        USERNAME.set(username);
        ROLES.set(roles);
    }

    public static Long getUserId() {
        String id = USER_ID.get();
        return id != null ? Long.parseLong(id) : null;
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static String getRoles() {
        return ROLES.get();
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLES.remove();
    }
}
