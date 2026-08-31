package com.pollbot.service;

import com.pollbot.model.Poll;
import com.pollbot.model.Question;
import com.pollbot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages poll lifecycle: creation, scheduling, answer tracking, and closing.
 */
public class PollManager {

    private static final Logger log = LoggerFactory.getLogger(PollManager.class);

    /**
     * Listener interface for poll state change events.
     */
    public interface PollListener {
        void onPollScheduled(Poll poll, long delayMinutes);
        void onPollStarted(Poll poll);
        void onAnswerReceived(Poll poll, long chatId);
        void onPollCompleted(Poll poll);
        void onCountdownTick(long remainingSeconds);
        void onScheduleCountdownTick(long remainingSeconds);
    }

    private Poll activePoll;
    private final CopyOnWriteArrayList<PollListener> listeners = new CopyOnWriteArrayList<>();
    private Timer pollTimer;
    private Timer reminderTimer;
    private Timer countdownTimer;
    private Timer scheduleCountdownTimer;
    private boolean reminderSent = false;

    // Callback to send poll via bot
    private PollSender pollSender;
    private ReminderSender reminderSender;
    private PollEndNotifier pollEndNotifier;

    @FunctionalInterface
    public interface PollSender {
        void sendPoll(Poll poll);
    }

    @FunctionalInterface
    public interface ReminderSender {
        void sendReminder(Poll poll, List<Long> chatIds);
    }

    @FunctionalInterface
    public interface PollEndNotifier {
        void notifyPollEnded(Poll poll);
    }

    public void addListener(PollListener listener) {
        listeners.add(listener);
    }

    public void setPollSender(PollSender sender) {
        this.pollSender = sender;
    }

    public void setReminderSender(ReminderSender sender) {
        this.reminderSender = sender;
    }

    public void setPollEndNotifier(PollEndNotifier notifier) {
        this.pollEndNotifier = notifier;
    }

    public boolean hasActivePoll() {
        return activePoll != null && !activePoll.isCompleted();
    }

    public Poll getActivePoll() {
        return activePoll;
    }

    /**
     * Creates and starts/schedules a new poll.
     * @param questions The poll questions (1-3)
     * @param communityMembers The current community members (snapshot)
     * @param delayMinutes 0 for immediate, >0 for delayed start
     * @return error message if validation fails, null on success
     */
    public String createPoll(List<Question> questions, List<User> communityMembers, long delayMinutes) {
        // Validation
        if (hasActivePoll()) {
            return "⚠️ קיים סקר פעיל. יש להמתין לסיומו לפני יצירת סקר חדש.";
        }
        if (communityMembers.size() < 3) {
            return "⚠️ נדרשים לפחות 3 חברי קהילה כדי להתחיל סקר. כרגע יש " + communityMembers.size() + " חברים.";
        }
        if (questions.isEmpty() || questions.size() > 3) {
            return "⚠️ סקר חייב לכלול בין שאלה אחת ל-3 שאלות.";
        }

        // Create the poll
        Poll poll = new Poll(questions, delayMinutes);

        // Snapshot participants
        for (User member : communityMembers) {
            poll.addParticipant(member);
        }

        this.activePoll = poll;
        this.reminderSent = false;

        if (delayMinutes > 0) {
            // Schedule for later
            poll.setStatus(Poll.Status.SCHEDULED);
            scheduleDelayedStart(poll, delayMinutes);
            for (PollListener listener : listeners) {
                listener.onPollScheduled(poll, delayMinutes);
            }
        } else {
            // Start immediately
            startPoll(poll);
        }

        return null; // success
    }

    private void scheduleDelayedStart(Poll poll, long delayMinutes) {
        long delayMs = delayMinutes * 60 * 1000;
        long startTimeMs = System.currentTimeMillis() + delayMs;

        // Schedule countdown timer (ticks every second)
        scheduleCountdownTimer = new Timer("schedule-countdown", true);
        scheduleCountdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long remaining = (startTimeMs - System.currentTimeMillis()) / 1000;
                if (remaining <= 0) {
                    scheduleCountdownTimer.cancel();
                    // Start the poll
                    startPoll(poll);
                } else {
                    for (PollListener listener : listeners) {
                        listener.onScheduleCountdownTick(remaining);
                    }
                }
            }
        }, 0, 1000);
    }

    private void startPoll(Poll poll) {
        poll.setStatus(Poll.Status.ACTIVE);
        poll.setStartTime(LocalDateTime.now());

        log.info("Poll started with {} participants and {} questions",
                poll.getParticipantCount(), poll.getQuestionCount());

        // Send poll to all participants via bot
        if (pollSender != null) {
            pollSender.sendPoll(poll);
        }

        // Notify listeners
        for (PollListener listener : listeners) {
            listener.onPollStarted(poll);
        }

        // Start 5-minute timer
        long pollDurationMs = 5 * 60 * 1000; // 5 minutes
        long endTimeMs = System.currentTimeMillis() + pollDurationMs;

        // Countdown timer (ticks every second)
        countdownTimer = new Timer("poll-countdown", true);
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long remaining = (endTimeMs - System.currentTimeMillis()) / 1000;
                if (remaining <= 0) {
                    closePoll("⏰ הזמן המוקצב הסתיים.");
                } else {
                    for (PollListener listener : listeners) {
                        listener.onCountdownTick(remaining);
                    }
                }
            }
        }, 0, 1000);

        // Reminder timer at 3 minutes
        reminderTimer = new Timer("poll-reminder", true);
        reminderTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendReminderToIncomplete(poll);
            }
        }, 3 * 60 * 1000); // 3 minutes
    }

    /**
     * Records an answer from a participant.
     * @param telegramPollId The Telegram poll ID
     * @param chatId The user's chat ID
     * @param optionIndex The selected option index
     */
    public void recordAnswer(String telegramPollId, long chatId, int optionIndex) {
        if (activePoll == null || !activePoll.isActive()) {
            log.warn("Received answer but no active poll");
            return;
        }

        Integer questionIndex = activePoll.getQuestionIndexByTelegramPollId(telegramPollId);
        if (questionIndex == null) {
            log.warn("Unknown Telegram poll ID: {}", telegramPollId);
            return;
        }

        if (!activePoll.getParticipants().containsKey(chatId)) {
            log.warn("User {} is not a participant of the active poll", chatId);
            return;
        }

        Question question = activePoll.getQuestions().get(questionIndex);
        boolean recorded = question.recordVote(chatId, optionIndex);

        if (recorded) {
            log.info("Recorded answer from {} for question {} option {}",
                    chatId, questionIndex, optionIndex);

            // Notify listeners
            for (PollListener listener : listeners) {
                listener.onAnswerReceived(activePoll, chatId);
            }

            // Check if all participants completed
            if (activePoll.allCompleted()) {
                closePoll("✅ כל המשתתפים השלימו את הסקר!");
            }
        }
    }

    private void sendReminderToIncomplete(Poll poll) {
        if (poll.isCompleted() || reminderSent) return;
        reminderSent = true;

        List<Long> incompleteUsers = new ArrayList<>();
        for (Long chatId : poll.getParticipants().keySet()) {
            if (!poll.hasCompleted(chatId)) {
                incompleteUsers.add(chatId);
            }
        }

        if (!incompleteUsers.isEmpty() && reminderSender != null) {
            log.info("Sending reminder to {} participants", incompleteUsers.size());
            reminderSender.sendReminder(poll, incompleteUsers);
        }
    }

    public synchronized void closePoll(String reason) {
        if (activePoll == null || activePoll.isCompleted()) return;

        log.info("Closing poll: {}", reason);
        activePoll.setStatus(Poll.Status.COMPLETED);
        activePoll.setEndTime(LocalDateTime.now());

        // Cancel timers
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        if (reminderTimer != null) {
            reminderTimer.cancel();
            reminderTimer = null;
        }
        if (scheduleCountdownTimer != null) {
            scheduleCountdownTimer.cancel();
            scheduleCountdownTimer = null;
        }

        // Notify bot to inform participants
        if (pollEndNotifier != null) {
            pollEndNotifier.notifyPollEnded(activePoll);
        }

        // Notify listeners (Swing)
        for (PollListener listener : listeners) {
            listener.onPollCompleted(activePoll);
        }
    }
}
