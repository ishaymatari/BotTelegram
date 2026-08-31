package com.pollbot.service;

import com.pollbot.model.User;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the global community of Telegram bot users.
 * Thread-safe: accessed from both Bot thread and Swing EDT.
 */
public class CommunityManager {

    /**
     * Listener interface for community change events.
     */
    public interface CommunityListener {
        void onMemberAdded(User user);
    }

    private final CopyOnWriteArrayList<User> members = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CommunityListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(CommunityListener listener) {
        listeners.add(listener);
    }

    /**
     * Adds a user to the community if not already a member.
     * @return true if the user was added (new member), false if already exists.
     */
    public boolean addUser(long chatId, String firstName, String username) {
        // Check if already a member
        if (isMember(chatId)) {
            return false;
        }

        User user = new User(chatId, firstName, username);
        members.add(user);

        // Notify listeners
        for (CommunityListener listener : listeners) {
            listener.onMemberAdded(user);
        }

        return true;
    }

    public boolean isMember(long chatId) {
        for (User member : members) {
            if (member.getChatId() == chatId) {
                return true;
            }
        }
        return false;
    }

    public List<User> getMembers() {
        return List.copyOf(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    /**
     * Returns all member chat IDs except the given one (for broadcast notifications).
     */
    public List<Long> getOtherMemberChatIds(long excludeChatId) {
        return members.stream()
                .map(User::getChatId)
                .filter(id -> id != excludeChatId)
                .toList();
    }
}
