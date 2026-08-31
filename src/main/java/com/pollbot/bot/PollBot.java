package com.pollbot.bot;

import com.pollbot.model.Poll;
import com.pollbot.model.Question;
import com.pollbot.model.User;
import com.pollbot.service.CommunityManager;
import com.pollbot.service.PollManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Telegram bot that handles community registration, poll delivery and answer collection.
 */
public class PollBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(PollBot.class);

    private final TelegramClient telegramClient;
    private final CommunityManager communityManager;
    private final PollManager pollManager;

    public PollBot(String botToken, CommunityManager communityManager, PollManager pollManager) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.communityManager = communityManager;
        this.pollManager = pollManager;
        setupPollManagerCallbacks();
    }

    private void setupPollManagerCallbacks() {
        // When poll needs to be sent to participants
        pollManager.setPollSender(this::sendPollToParticipants);

        // When reminders need to be sent
        pollManager.setReminderSender(this::sendReminders);

        // When poll ends, notify participants
        pollManager.setPollEndNotifier(this::notifyPollEnded);
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasPollAnswer()) {
                handlePollAnswer(update.getPollAnswer());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    // ──────────────────────────────────────────
    // Message handling
    // ──────────────────────────────────────────

    private void handleMessage(Message message) {
        String text = message.getText().trim();
        long chatId = message.getChatId();
        org.telegram.telegrambots.meta.api.objects.User from = message.getFrom();
        String firstName = from.getFirstName();
        String username = from.getUserName(); // may be null

        log.info("Message from {} ({}): {}", firstName, chatId, text);

        // Check for join commands
        if (text.equals("/start") || text.equalsIgnoreCase("היי") || text.equalsIgnoreCase("Hi")) {
            handleJoin(chatId, firstName, username);
        } else {
            sendText(chatId,
                "🤖 לא הצלחתי להבין את ההודעה שלך.\n\n" +
                "כדי להצטרף לקהילה, שלח/י:\n" +
                "• /start\n" +
                "• היי\n" +
                "• Hi");
        }
    }

    private void handleJoin(long chatId, String firstName, String username) {
        if (communityManager.isMember(chatId)) {
            // Already a member
            sendText(chatId,
                "👋 היי " + firstName + "!\n\n" +
                "את/ה כבר חבר/ה בקהילה שלנו. 😊\n" +
                "אנחנו כרגע " + communityManager.getMemberCount() + " חברים.\n\n" +
                "כשיהיה סקר חדש, תקבל/י אותו כאן. 📊");
            return;
        }

        // Add to community
        boolean added = communityManager.addUser(chatId, firstName, username);
        if (!added) return; // shouldn't happen, but safety check

        int memberCount = communityManager.getMemberCount();

        // Welcome the new member
        sendText(chatId,
            "🎉 ברוך/ה הבא/ה לקהילה, " + firstName + "!\n\n" +
            "הצטרפת בהצלחה! 🥳\n" +
            "את/ה חבר/ה מספר " + memberCount + " בקהילה.\n\n" +
            "כשיהיה סקר חדש, תקבל/י אותו ישירות כאן.\n" +
            "תודה שהצטרפת! 💜");

        // Notify all other members
        String notification =
            "📢 חבר/ה חדש/ה הצטרף/ה לקהילה!\n\n" +
            "👤 " + firstName + (username != null ? " (@" + username + ")" : "") + "\n" +
            "👥 גודל הקהילה: " + memberCount + " חברים";

        List<Long> otherMembers = communityManager.getOtherMemberChatIds(chatId);
        for (Long memberId : otherMembers) {
            sendText(memberId, notification);
        }

        log.info("New member joined: {} ({}). Community size: {}", firstName, chatId, memberCount);
    }

    // ──────────────────────────────────────────
    // Poll sending
    // ──────────────────────────────────────────

    private void sendPollToParticipants(Poll poll) {
        log.info("Sending poll to {} participants", poll.getParticipantCount());

        for (int i = 0; i < poll.getQuestionCount(); i++) {
            Question question = poll.getQuestions().get(i);

            for (User participant : poll.getParticipants().values()) {
                try {
                    List<InputPollOption> optionTexts = question.getOptions().stream()
                            .map(text -> new InputPollOption(text))
                            .toList();

                    SendPoll sendPoll = SendPoll.builder()
                            .chatId(String.valueOf(participant.getChatId()))
                            .question("📊 שאלה " + (i + 1) + "/" + poll.getQuestionCount() + ": " + question.getText())
                            .options(optionTexts)
                            .isAnonymous(false)
                            .allowMultipleAnswers(false)
                            .build();

                    Message sentMessage = telegramClient.execute(sendPoll);

                    // Map Telegram's poll ID to our question index
                    if (sentMessage.getPoll() != null) {
                        String telegramPollId = sentMessage.getPoll().getId();
                        poll.mapTelegramPollId(telegramPollId, i);
                        log.info("Mapped Telegram poll {} to question {}", telegramPollId, i);
                    }
                } catch (TelegramApiException e) {
                    log.error("Failed to send poll to user {}", participant.getChatId(), e);
                }
            }
        }

        // Notify participants that the poll started
        for (User participant : poll.getParticipants().values()) {
            sendText(participant.getChatId(),
                "📋 סקר חדש נשלח אליך!\n\n" +
                "📝 " + poll.getQuestionCount() + " שאלות\n" +
                "⏱️ יש לך 5 דקות לענות.\n\n" +
                "בהצלחה! 🍀");
        }
    }

    // ──────────────────────────────────────────
    // Poll answer handling
    // ──────────────────────────────────────────

    private void handlePollAnswer(PollAnswer pollAnswer) {
        String pollId = pollAnswer.getPollId();
        org.telegram.telegrambots.meta.api.objects.User voter = pollAnswer.getUser();
        List<Integer> optionIds = pollAnswer.getOptionIds();

        if (optionIds == null || optionIds.isEmpty()) {
            log.info("User {} retracted vote on poll {}", voter.getId(), pollId);
            return;
        }

        long chatId = voter.getId();
        int optionIndex = optionIds.get(0); // Single answer only

        log.info("Poll answer from {} ({}) on poll {}: option {}",
                voter.getFirstName(), chatId, pollId, optionIndex);

        pollManager.recordAnswer(pollId, chatId, optionIndex);
    }

    // ──────────────────────────────────────────
    // Reminders
    // ──────────────────────────────────────────

    private void sendReminders(Poll poll, List<Long> chatIds) {
        for (Long chatId : chatIds) {
            int answered = poll.getAnsweredCount(chatId);
            int total = poll.getQuestionCount();

            String message;
            if (answered == 0) {
                message =
                    "⏰ תזכורת!\n\n" +
                    "טרם ענית על הסקר.\n" +
                    "נותרו לך כ-2 דקות להשלים " + total + " שאלות.\n\n" +
                    "אנא ענה/י בהקדם! 🙏";
            } else {
                message =
                    "⏰ תזכורת!\n\n" +
                    "ענית על " + answered + " מתוך " + total + " שאלות.\n" +
                    "נותרו לך כ-2 דקות להשלים את הסקר.\n\n" +
                    "אנא השלם/י את השאלות הנותרות! 🙏";
            }

            sendText(chatId, message);
        }
        log.info("Sent reminders to {} participants", chatIds.size());
    }

    // ──────────────────────────────────────────
    // Poll end notification
    // ──────────────────────────────────────────

    private void notifyPollEnded(Poll poll) {
        for (User participant : poll.getParticipants().values()) {
            String status = poll.hasCompleted(participant.getChatId())
                ? "✅ השלמת את כל השאלות. תודה!"
                : "⚠️ לא הספקת להשלים את כל השאלות.";

            sendText(participant.getChatId(),
                "🏁 הסקר הסתיים!\n\n" +
                status + "\n\n" +
                "תודה על ההשתתפות! 💜");
        }
    }

    // ──────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────

    private void sendText(long chatId, String text) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .build();
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}", chatId, e);
        }
    }
}
