package com.pollbot.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a community member registered with the Telegram bot.
 */
public class User {

    private final long chatId;
    private final String firstName;
    private final String username; // may be null
    private final LocalDateTime joinedAt;

    public User(long chatId, String firstName, String username) {
        this.chatId = chatId;
        this.firstName = firstName;
        this.username = username;
        this.joinedAt = LocalDateTime.now();
    }

    public long getChatId() {
        return chatId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayUsername() {
        return username != null ? "@" + username : "—";
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public String getFormattedJoinTime() {
        return joinedAt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return chatId == user.chatId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(chatId);
    }

    @Override
    public String toString() {
        return firstName + " (" + getDisplayUsername() + ")";
    }
}
