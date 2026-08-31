package com.pollbot;

import com.pollbot.bot.PollBot;
import com.pollbot.service.ChatGPTService;
import com.pollbot.service.CommunityManager;
import com.pollbot.service.PollManager;
import com.pollbot.ui.SwingUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // TODO: Replace with your actual OpenAI API Key if you want to use the ChatGPT feature
    private static final String OPENAI_API_KEY = "YOUR_OPENAI_API_KEY"; 
    
    // The Telegram Bot Token provided in the instructions
    private static final String BOT_TOKEN = "8619230010:AAEZvEJGGPUresJJkIkXLgGRLl60RayOhgA";

    public static void main(String[] args) {
        log.info("Starting PollBot Application...");

        // 1. Set System Look and Feel for Swing
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("Could not set system look and feel", e);
        }

        // 2. Initialize Core Services
        CommunityManager communityManager = new CommunityManager();
        PollManager pollManager = new PollManager();
        ChatGPTService chatGPTService = new ChatGPTService(OPENAI_API_KEY);

        // 3. Initialize Telegram Bot
        PollBot pollBot = new PollBot(BOT_TOKEN, communityManager, pollManager);
        
        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        try {
            botsApplication.registerBot(BOT_TOKEN, pollBot);
            log.info("Telegram Bot registered successfully.");
        } catch (Exception e) {
            log.error("Failed to register Telegram Bot", e);
            JOptionPane.showMessageDialog(null, 
                "שגיאה בהפעלת הבוט. ודא שהטוקן תקין ויש חיבור לאינטרנט.\n" + e.getMessage(), 
                "שגיאת אתחול", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // 4. Launch Swing UI on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            SwingUI ui = new SwingUI(communityManager, pollManager, chatGPTService);
            
            // Handle graceful shutdown
            ui.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    log.info("Shutting down application...");
                    try {
                        botsApplication.stop();
                        botsApplication.close();
                    } catch (Exception ex) {
                        log.error("Error stopping bots application", ex);
                    }
                    System.exit(0);
                }
            });
            
            ui.setVisible(true);
        });
    }
}
