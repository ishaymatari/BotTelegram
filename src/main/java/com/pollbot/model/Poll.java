package com.pollbot.model;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a poll with 1-3 questions, participants and their statuses.
 */
public class Poll {

    public enum Status {
        SCHEDULED, ACTIVE, COMPLETED
    }

    private final List<Question> questions;
    private final Map<Long, User> participants; // chatId -> User (snapshot at poll start)
    private final Map<String, Integer> telegramPollIdToQuestionIndex; // Telegram poll ID -> question index
    private Status status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private final long scheduledDelayMinutes;

    public Poll(List<Question> questions, long scheduledDelayMinutes) {
        if (questions.isEmpty() || questions.size() > 3) {
            throw new IllegalArgumentException("A poll must have 1-3 questions, got " + questions.size());
        }
        this.questions = new ArrayList<>(questions);
        this.participants = new ConcurrentHashMap<>();
        this.telegramPollIdToQuestionIndex = new ConcurrentHashMap<>();
        this.status = scheduledDelayMinutes > 0 ? Status.SCHEDULED : Status.ACTIVE;
        this.scheduledDelayMinutes = scheduledDelayMinutes;
    }

    // --- Questions ---

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getQuestionCount() {
        return questions.size();
    }

    // --- Participants ---

    public void addParticipant(User user) {
        participants.put(user.getChatId(), user);
    }

    public Map<Long, User> getParticipants() {
        return Collections.unmodifiableMap(participants);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    // --- Telegram Poll ID mapping ---

    public void mapTelegramPollId(String telegramPollId, int questionIndex) {
        telegramPollIdToQuestionIndex.put(telegramPollId, questionIndex);
    }

    public Integer getQuestionIndexByTelegramPollId(String telegramPollId) {
        return telegramPollIdToQuestionIndex.get(telegramPollId);
    }

    // --- Status ---

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public long getScheduledDelayMinutes() {
        return scheduledDelayMinutes;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    // --- Progress tracking ---

    /**
     * Returns the number of questions answered by a specific user in this poll.
     */
    public int getAnsweredCount(long chatId) {
        int count = 0;
        for (Question q : questions) {
            if (q.hasAnswered(chatId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true if the user has answered ALL questions.
     */
    public boolean hasCompleted(long chatId) {
        return getAnsweredCount(chatId) == questions.size();
    }

    /**
     * Returns the number of participants who have completed all questions.
     */
    public int getCompletedCount() {
        int count = 0;
        for (Long chatId : participants.keySet()) {
            if (hasCompleted(chatId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true if ALL participants have completed all questions.
     */
    public boolean allCompleted() {
        if (participants.isEmpty()) return false;
        for (Long chatId : participants.keySet()) {
            if (!hasCompleted(chatId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the display status for a participant.
     */
    public String getParticipantStatus(long chatId) {
        int answered = getAnsweredCount(chatId);
        int total = questions.size();
        if (answered == 0) return "טרם ענה";
        if (answered == total) return "השלים ✅";
        return "בתהליך...";
    }

    /**
     * Returns progress string like "2/3".
     */
    public String getProgressString(long chatId) {
        return getAnsweredCount(chatId) + "/" + questions.size();
    }
}
