package com.pollbot.ui;

import com.pollbot.model.Poll;
import com.pollbot.model.Question;
import com.pollbot.model.User;
import com.pollbot.service.ChatGPTService;
import com.pollbot.service.CommunityManager;
import com.pollbot.service.PollManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main Swing UI for managing the Telegram poll bot.
 * Displays community members, poll creation, live poll tracking, and results.
 */
public class SwingUI extends JFrame implements CommunityManager.CommunityListener, PollManager.PollListener {

    private final CommunityManager communityManager;
    private final PollManager pollManager;
    private final ChatGPTService chatGPTService;

    // ── Community Panel ──
    private DefaultTableModel communityTableModel;
    private JLabel communityCountLabel;

    // ── Poll Creation Panel ──
    private JTabbedPane createPollTabs;
    // Manual tab
    private final List<JTextField> manualQuestionFields = new ArrayList<>();
    private final List<List<JTextField>> manualOptionFields = new ArrayList<>();
    private JPanel manualQuestionsPanel;
    private JSpinner manualQuestionCountSpinner;
    // ChatGPT tab
    private JTextField topicField;
    private JSpinner gptQuestionCountSpinner;
    private JButton generateButton;
    private JTextArea generatedPreview;
    private List<Question> generatedQuestions;
    // Send options
    private JRadioButton immediateRadio;
    private JRadioButton delayedRadio;
    private JSpinner delaySpinner;
    private JButton sendPollButton;

    // ── Active Poll Panel ──
    private JPanel activePollPanel;
    private DefaultTableModel pollTableModel;
    private JLabel pollStatusLabel;
    private JLabel pollCountdownLabel;
    private JLabel pollStatsLabel;
    private JLabel scheduleCountdownLabel;
    private CardLayout activePollCardLayout;
    private JPanel activePollCardPanel;

    // ── Results Panel ──
    private JPanel resultsPanel;
    private JPanel resultsContentPanel;

    // Colors
    private static final Color BG_COLOR = new Color(245, 247, 250);
    private static final Color HEADER_COLOR = new Color(63, 81, 181);
    private static final Color SUCCESS_COLOR = new Color(76, 175, 80);
    private static final Color WARNING_COLOR = new Color(255, 152, 0);
    private static final Color DANGER_COLOR = new Color(244, 67, 54);
    private static final Color CARD_COLOR = Color.WHITE;

    public SwingUI(CommunityManager communityManager, PollManager pollManager, ChatGPTService chatGPTService) {
        this.communityManager = communityManager;
        this.pollManager = pollManager;
        this.chatGPTService = chatGPTService;

        // Register as listeners
        communityManager.addListener(this);
        pollManager.addListener(this);

        initUI();
    }

    private void initUI() {
        setTitle("📊 מערכת ניהול סקרים — Telegram Poll Bot");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(BG_COLOR);

        // Header
        mainPanel.add(createHeader(), BorderLayout.NORTH);

        // Content: Left (Community) | Center (Create Poll) | Right (Active Poll)
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(BG_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weighty = 1.0;

        // Left — Community
        gbc.gridx = 0;
        gbc.weightx = 0.25;
        contentPanel.add(createCommunityPanel(), gbc);

        // Center — Poll Creation
        gbc.gridx = 1;
        gbc.weightx = 0.40;
        contentPanel.add(createPollCreationPanel(), gbc);

        // Right — Active Poll / Results
        gbc.gridx = 2;
        gbc.weightx = 0.35;
        contentPanel.add(createActivePollPanel(), gbc);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    // ══════════════════════════════════════════
    // HEADER
    // ══════════════════════════════════════════

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_COLOR);
        header.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel title = new JLabel("📊 מערכת ניהול סקרים — Telegram Poll Bot");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JLabel status = new JLabel("🟢 הבוט פעיל");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        status.setForeground(new Color(200, 255, 200));
        header.add(status, BorderLayout.EAST);

        return header;
    }

    // ══════════════════════════════════════════
    // COMMUNITY PANEL
    // ══════════════════════════════════════════

    private JPanel createCommunityPanel() {
        JPanel panel = createCardPanel("👥 קהילה");

        communityCountLabel = new JLabel("חברי קהילה: 0");
        communityCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        communityCountLabel.setForeground(HEADER_COLOR);
        communityCountLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(communityCountLabel, BorderLayout.NORTH);

        // Table
        String[] columns = {"שם", "Telegram", "הצטרפות"};
        communityTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(communityTableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(30);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(HEADER_COLOR);
        table.getTableHeader().setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(200, 220, 255));

        // Right-align for Hebrew
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Load existing members
        for (User member : communityManager.getMembers()) {
            communityTableModel.addRow(new Object[]{
                member.getFirstName(),
                member.getDisplayUsername(),
                member.getFormattedJoinTime()
            });
        }
        communityCountLabel.setText("👥 חברי קהילה: " + communityManager.getMemberCount());

        return panel;
    }

    // ══════════════════════════════════════════
    // POLL CREATION PANEL
    // ══════════════════════════════════════════

    private JPanel createPollCreationPanel() {
        JPanel panel = createCardPanel("📝 יצירת סקר");
        panel.setLayout(new BorderLayout(5, 5));

        createPollTabs = new JTabbedPane();
        createPollTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Manual tab
        createPollTabs.addTab("✏️ יצירה ידנית", createManualTab());

        // ChatGPT tab
        createPollTabs.addTab("🤖 ChatGPT", createChatGPTTab());

        panel.add(createPollTabs, BorderLayout.CENTER);

        // Bottom: Send options
        panel.add(createSendOptionsPanel(), BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createManualTab() {
        JPanel tab = new JPanel(new BorderLayout(5, 5));
        tab.setBorder(new EmptyBorder(10, 10, 10, 10));
        tab.setBackground(CARD_COLOR);

        // Question count selector
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.setBackground(CARD_COLOR);
        topPanel.add(new JLabel("מספר שאלות:"));
        manualQuestionCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 3, 1));
        manualQuestionCountSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        manualQuestionCountSpinner.addChangeListener(e -> rebuildManualQuestions());
        topPanel.add(manualQuestionCountSpinner);
        tab.add(topPanel, BorderLayout.NORTH);

        // Questions panel (scrollable)
        manualQuestionsPanel = new JPanel();
        manualQuestionsPanel.setLayout(new BoxLayout(manualQuestionsPanel, BoxLayout.Y_AXIS));
        manualQuestionsPanel.setBackground(CARD_COLOR);

        JScrollPane scroll = new JScrollPane(manualQuestionsPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        tab.add(scroll, BorderLayout.CENTER);

        rebuildManualQuestions();
        return tab;
    }

    private void rebuildManualQuestions() {
        int count = (int) manualQuestionCountSpinner.getValue();
        manualQuestionsPanel.removeAll();
        manualQuestionFields.clear();
        manualOptionFields.clear();

        for (int i = 0; i < count; i++) {
            JPanel qPanel = new JPanel();
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(HEADER_COLOR, 1, true),
                    "שאלה " + (i + 1),
                    TitledBorder.RIGHT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 13), HEADER_COLOR),
                new EmptyBorder(5, 10, 5, 10)
            ));
            qPanel.setBackground(CARD_COLOR);
            qPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));

            // Question text
            JTextField qField = new JTextField();
            qField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            qField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(5, 8, 5, 8)
            ));
            qField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            JPanel qFieldPanel = new JPanel(new BorderLayout());
            qFieldPanel.setBackground(CARD_COLOR);
            qFieldPanel.add(new JLabel("נוסח השאלה: "), BorderLayout.EAST);
            qFieldPanel.add(qField, BorderLayout.CENTER);
            qFieldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
            qPanel.add(qFieldPanel);
            qPanel.add(Box.createVerticalStrut(5));
            manualQuestionFields.add(qField);

            // Options (4 fields, minimum 2 required)
            List<JTextField> optFields = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                JTextField optField = new JTextField();
                optField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                optField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(3, 6, 3, 6)
                ));
                optField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

                JPanel optPanel = new JPanel(new BorderLayout(5, 0));
                optPanel.setBackground(CARD_COLOR);
                String label = (j < 2) ? "תשובה " + (j + 1) + " *: " : "תשובה " + (j + 1) + ":   ";
                JLabel optLabel = new JLabel(label);
                optLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                if (j >= 2) optLabel.setForeground(Color.GRAY);
                optPanel.add(optLabel, BorderLayout.EAST);
                optPanel.add(optField, BorderLayout.CENTER);
                optPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                qPanel.add(optPanel);
                qPanel.add(Box.createVerticalStrut(3));
                optFields.add(optField);
            }
            manualOptionFields.add(optFields);

            manualQuestionsPanel.add(qPanel);
            manualQuestionsPanel.add(Box.createVerticalStrut(10));
        }

        manualQuestionsPanel.revalidate();
        manualQuestionsPanel.repaint();
    }

    private JPanel createChatGPTTab() {
        JPanel tab = new JPanel(new BorderLayout(5, 10));
        tab.setBorder(new EmptyBorder(10, 10, 10, 10));
        tab.setBackground(CARD_COLOR);

        // Top: topic input
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(CARD_COLOR);

        JLabel topicLabel = new JLabel("נושא הסקר:");
        topicLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topPanel.add(topicLabel, BorderLayout.NORTH);

        topicField = new JTextField();
        topicField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        topicField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            new EmptyBorder(6, 8, 6, 8)
        ));
        topicField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        topPanel.add(topicField, BorderLayout.CENTER);

        JPanel countAndBtn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        countAndBtn.setBackground(CARD_COLOR);

        countAndBtn.add(new JLabel("מספר שאלות:"));
        gptQuestionCountSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 3, 1));
        countAndBtn.add(gptQuestionCountSpinner);

        generateButton = new JButton("🤖 צור שאלות");
        generateButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        generateButton.setBackground(HEADER_COLOR);
        generateButton.setForeground(Color.WHITE);
        generateButton.setFocusPainted(false);
        generateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        generateButton.addActionListener(this::onGenerateClicked);
        countAndBtn.add(generateButton);

        topPanel.add(countAndBtn, BorderLayout.SOUTH);
        tab.add(topPanel, BorderLayout.NORTH);

        // Preview area
        generatedPreview = new JTextArea();
        generatedPreview.setEditable(false);
        generatedPreview.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        generatedPreview.setLineWrap(true);
        generatedPreview.setWrapStyleWord(true);
        generatedPreview.setBackground(new Color(250, 250, 250));
        generatedPreview.setBorder(new EmptyBorder(10, 10, 10, 10));
        generatedPreview.setText("💡 הזן נושא ולחץ 'צור שאלות' ליצירת סקר אוטומטי באמצעות ChatGPT.");
        generatedPreview.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JScrollPane previewScroll = new JScrollPane(generatedPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            "תצוגה מקדימה",
            TitledBorder.RIGHT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12), HEADER_COLOR
        ));
        tab.add(previewScroll, BorderLayout.CENTER);

        if (!chatGPTService.isAvailable()) {
            generateButton.setEnabled(false);
            topicField.setEnabled(false);
            generatedPreview.setText("⚠️ מפתח OpenAI API לא הוגדר.\nלא ניתן ליצור שאלות באופן אוטומטי.\n\nהשתמש/י בטאב 'יצירה ידנית'.");
        }

        return tab;
    }

    private JPanel createSendOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
            new EmptyBorder(10, 10, 5, 10)
        ));
        panel.setBackground(CARD_COLOR);

        // Timing options
        JPanel timingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        timingPanel.setBackground(CARD_COLOR);

        immediateRadio = new JRadioButton("שליחה מיידית");
        immediateRadio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        immediateRadio.setSelected(true);
        immediateRadio.setBackground(CARD_COLOR);

        delayedRadio = new JRadioButton("שליחה בעיכוב של");
        delayedRadio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        delayedRadio.setBackground(CARD_COLOR);

        ButtonGroup timingGroup = new ButtonGroup();
        timingGroup.add(immediateRadio);
        timingGroup.add(delayedRadio);

        delaySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 60, 1));
        delaySpinner.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        delaySpinner.setEnabled(false);

        JLabel minutesLabel = new JLabel("דקות");
        minutesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        delayedRadio.addActionListener(e -> delaySpinner.setEnabled(true));
        immediateRadio.addActionListener(e -> delaySpinner.setEnabled(false));

        timingPanel.add(minutesLabel);
        timingPanel.add(delaySpinner);
        timingPanel.add(delayedRadio);
        timingPanel.add(immediateRadio);

        panel.add(timingPanel);

        // Schedule countdown label (shown when poll is scheduled)
        scheduleCountdownLabel = new JLabel(" ");
        scheduleCountdownLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        scheduleCountdownLabel.setForeground(WARNING_COLOR);
        scheduleCountdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        scheduleCountdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(scheduleCountdownLabel);

        // Send button
        sendPollButton = new JButton("🚀 שלח סקר");
        sendPollButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sendPollButton.setBackground(SUCCESS_COLOR);
        sendPollButton.setForeground(Color.WHITE);
        sendPollButton.setFocusPainted(false);
        sendPollButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendPollButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        sendPollButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        sendPollButton.addActionListener(this::onSendPollClicked);

        panel.add(Box.createVerticalStrut(5));
        panel.add(sendPollButton);

        return panel;
    }

    // ══════════════════════════════════════════
    // ACTIVE POLL PANEL
    // ══════════════════════════════════════════

    private JPanel createActivePollPanel() {
        JPanel outerPanel = createCardPanel("📊 סקר פעיל / תוצאות");
        outerPanel.setLayout(new BorderLayout());

        activePollCardLayout = new CardLayout();
        activePollCardPanel = new JPanel(activePollCardLayout);
        activePollCardPanel.setBackground(CARD_COLOR);

        // Card 1: No active poll
        JPanel noActivePoll = new JPanel(new BorderLayout());
        noActivePoll.setBackground(CARD_COLOR);
        JLabel noPollLabel = new JLabel("<html><center>📭<br><br>אין סקר פעיל כרגע.<br>צור סקר חדש בפאנל השמאלי.</center></html>");
        noPollLabel.setHorizontalAlignment(SwingConstants.CENTER);
        noPollLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        noPollLabel.setForeground(Color.GRAY);
        noActivePoll.add(noPollLabel, BorderLayout.CENTER);
        activePollCardPanel.add(noActivePoll, "NONE");

        // Card 2: Active poll tracking
        activePollPanel = new JPanel(new BorderLayout(5, 5));
        activePollPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        activePollPanel.setBackground(CARD_COLOR);

        // Stats panel at top
        JPanel statsPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        statsPanel.setBackground(new Color(235, 240, 255));
        statsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        pollCountdownLabel = new JLabel("⏱️ זמן שנותר: 05:00");
        pollCountdownLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pollCountdownLabel.setForeground(HEADER_COLOR);
        pollCountdownLabel.setHorizontalAlignment(SwingConstants.CENTER);

        pollStatsLabel = new JLabel("📈 משתתפים: 0 | השלימו: 0 | טרם השלימו: 0");
        pollStatsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        pollStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        pollStatusLabel = new JLabel("🟢 סקר פעיל");
        pollStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pollStatusLabel.setForeground(SUCCESS_COLOR);
        pollStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        statsPanel.add(pollCountdownLabel);
        statsPanel.add(pollStatusLabel);
        statsPanel.add(pollStatsLabel);

        activePollPanel.add(statsPanel, BorderLayout.NORTH);

        // Participants table
        String[] pollColumns = {"שם", "התקדמות", "מצב"};
        pollTableModel = new DefaultTableModel(pollColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable pollTable = new JTable(pollTableModel);
        pollTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        pollTable.setRowHeight(32);
        pollTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        pollTable.getTableHeader().setBackground(HEADER_COLOR);
        pollTable.getTableHeader().setForeground(Color.WHITE);

        // Custom renderer for status column coloring
        pollTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                String text = value != null ? value.toString() : "";
                if (text.contains("השלים")) {
                    setForeground(SUCCESS_COLOR);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (text.contains("בתהליך")) {
                    setForeground(WARNING_COLOR);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(DANGER_COLOR);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });

        // Center align progress column
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        pollTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);

        // Right align name column
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        pollTable.getColumnModel().getColumn(0).setCellRenderer(rightRenderer);

        JScrollPane pollScroll = new JScrollPane(pollTable);
        pollScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        activePollPanel.add(pollScroll, BorderLayout.CENTER);

        activePollCardPanel.add(activePollPanel, "ACTIVE");

        // Card 3: Results
        resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBackground(CARD_COLOR);
        resultsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel resultsTitle = new JLabel("📊 תוצאות הסקר");
        resultsTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        resultsTitle.setForeground(HEADER_COLOR);
        resultsTitle.setHorizontalAlignment(SwingConstants.CENTER);
        resultsTitle.setBorder(new EmptyBorder(5, 0, 10, 0));
        resultsPanel.add(resultsTitle, BorderLayout.NORTH);

        resultsContentPanel = new JPanel();
        resultsContentPanel.setLayout(new BoxLayout(resultsContentPanel, BoxLayout.Y_AXIS));
        resultsContentPanel.setBackground(CARD_COLOR);

        JScrollPane resultsScroll = new JScrollPane(resultsContentPanel);
        resultsScroll.setBorder(null);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(16);
        resultsPanel.add(resultsScroll, BorderLayout.CENTER);

        // "New poll" button at bottom of results
        JButton newPollButton = new JButton("📝 צור סקר חדש");
        newPollButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        newPollButton.setBackground(HEADER_COLOR);
        newPollButton.setForeground(Color.WHITE);
        newPollButton.setFocusPainted(false);
        newPollButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newPollButton.addActionListener(e -> {
            activePollCardLayout.show(activePollCardPanel, "NONE");
            sendPollButton.setEnabled(true);
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setBackground(CARD_COLOR);
        btnPanel.add(newPollButton);
        resultsPanel.add(btnPanel, BorderLayout.SOUTH);

        activePollCardPanel.add(resultsPanel, "RESULTS");

        outerPanel.add(activePollCardPanel, BorderLayout.CENTER);

        // Show "no active poll" initially
        activePollCardLayout.show(activePollCardPanel, "NONE");

        return outerPanel;
    }

    // ══════════════════════════════════════════
    // HELPER: Card panel
    // ══════════════════════════════════════════

    private JPanel createCardPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(CARD_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                title,
                TitledBorder.RIGHT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14), HEADER_COLOR
            ),
            new EmptyBorder(8, 8, 8, 8)
        ));
        return panel;
    }

    // ══════════════════════════════════════════
    // ACTION HANDLERS
    // ══════════════════════════════════════════

    private void onGenerateClicked(ActionEvent e) {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "⚠️ נא להזין נושא לסקר.",
                "שגיאה", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int qCount = (int) gptQuestionCountSpinner.getValue();

        generateButton.setEnabled(false);
        generateButton.setText("⏳ יוצר שאלות...");
        generatedPreview.setText("⏳ מתחבר ל-ChatGPT...\nנא להמתין...");

        SwingWorker<List<Question>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Question> doInBackground() throws Exception {
                return chatGPTService.generateQuestions(topic, qCount);
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                generateButton.setText("🤖 צור שאלות");
                try {
                    generatedQuestions = get();
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ נוצרו ").append(generatedQuestions.size()).append(" שאלות:\n\n");
                    for (int i = 0; i < generatedQuestions.size(); i++) {
                        Question q = generatedQuestions.get(i);
                        sb.append("📌 שאלה ").append(i + 1).append(": ").append(q.getText()).append("\n");
                        for (int j = 0; j < q.getOptions().size(); j++) {
                            sb.append("   ").append((char)('א' + j)).append(". ").append(q.getOptions().get(j)).append("\n");
                        }
                        sb.append("\n");
                    }
                    sb.append("💡 לחץ 'שלח סקר' לשליחת הסקר לחברי הקהילה.");
                    generatedPreview.setText(sb.toString());
                } catch (Exception ex) {
                    generatedPreview.setText("❌ שגיאה ביצירת שאלות:\n" + ex.getMessage());
                    generatedQuestions = null;
                }
            }
        };
        worker.execute();
    }

    private void onSendPollClicked(ActionEvent e) {
        List<Question> questions;

        // Determine source: manual or ChatGPT
        int selectedTab = createPollTabs.getSelectedIndex();
        if (selectedTab == 0) {
            // Manual
            questions = collectManualQuestions();
            if (questions == null) return; // validation failed
        } else {
            // ChatGPT
            if (generatedQuestions == null || generatedQuestions.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "⚠️ לא נוצרו שאלות עדיין.\nנא ליצור שאלות באמצעות ChatGPT תחילה.",
                    "שגיאה", JOptionPane.WARNING_MESSAGE);
                return;
            }
            questions = generatedQuestions;
        }

        // Delay
        long delayMinutes = 0;
        if (delayedRadio.isSelected()) {
            delayMinutes = (int) delaySpinner.getValue();
        }

        // Create the poll
        String error = pollManager.createPoll(questions, communityManager.getMembers(), delayMinutes);
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "שגיאה", JOptionPane.WARNING_MESSAGE);
            return;
        }

        sendPollButton.setEnabled(false);

        if (delayMinutes > 0) {
            JOptionPane.showMessageDialog(this,
                "⏱️ הסקר תוזמן לשליחה בעוד " + delayMinutes + " דקות.\nספירה לאחור מוצגת בממשק.",
                "סקר תוזמן", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                "✅ הסקר נשלח בהצלחה לכל חברי הקהילה!",
                "סקר נשלח", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private List<Question> collectManualQuestions() {
        List<Question> questions = new ArrayList<>();
        int count = (int) manualQuestionCountSpinner.getValue();

        for (int i = 0; i < count; i++) {
            String qText = manualQuestionFields.get(i).getText().trim();
            if (qText.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "⚠️ נא למלא את נוסח שאלה " + (i + 1) + ".",
                    "שגיאה", JOptionPane.WARNING_MESSAGE);
                return null;
            }

            List<String> options = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                String opt = manualOptionFields.get(i).get(j).getText().trim();
                if (!opt.isEmpty()) {
                    options.add(opt);
                }
            }

            if (options.size() < 2) {
                JOptionPane.showMessageDialog(this,
                    "⚠️ שאלה " + (i + 1) + " חייבת לכלול לפחות 2 תשובות.",
                    "שגיאה", JOptionPane.WARNING_MESSAGE);
                return null;
            }

            questions.add(new Question(qText, options));
        }

        return questions;
    }

    // ══════════════════════════════════════════
    // LISTENER IMPLEMENTATIONS (Community)
    // ══════════════════════════════════════════

    @Override
    public void onMemberAdded(User user) {
        SwingUtilities.invokeLater(() -> {
            communityTableModel.addRow(new Object[]{
                user.getFirstName(),
                user.getDisplayUsername(),
                user.getFormattedJoinTime()
            });
            communityCountLabel.setText("👥 חברי קהילה: " + communityManager.getMemberCount());
        });
    }

    // ══════════════════════════════════════════
    // LISTENER IMPLEMENTATIONS (Poll)
    // ══════════════════════════════════════════

    @Override
    public void onPollScheduled(Poll poll, long delayMinutes) {
        SwingUtilities.invokeLater(() -> {
            scheduleCountdownLabel.setText("⏱️ הסקר יישלח בעוד " + delayMinutes + " דקות...");
        });
    }

    @Override
    public void onPollStarted(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            scheduleCountdownLabel.setText("✅ הסקר נשלח!");
            // Populate the active poll table
            pollTableModel.setRowCount(0);
            for (User participant : poll.getParticipants().values()) {
                pollTableModel.addRow(new Object[]{
                    participant.getFirstName(),
                    poll.getProgressString(participant.getChatId()),
                    poll.getParticipantStatus(participant.getChatId())
                });
            }

            updatePollStats(poll);
            pollStatusLabel.setText("🟢 סקר פעיל");
            pollStatusLabel.setForeground(SUCCESS_COLOR);

            activePollCardLayout.show(activePollCardPanel, "ACTIVE");
        });
    }

    @Override
    public void onAnswerReceived(Poll poll, long chatId) {
        SwingUtilities.invokeLater(() -> {
            // Update the participant's row
            refreshPollTable(poll);
            updatePollStats(poll);
        });
    }

    @Override
    public void onPollCompleted(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            pollStatusLabel.setText("🏁 הסקר הסתיים");
            pollStatusLabel.setForeground(DANGER_COLOR);
            pollCountdownLabel.setText("⏱️ 00:00");

            // Show results after a brief delay
            Timer showResults = new Timer(1500, e -> showResults(poll));
            showResults.setRepeats(false);
            showResults.start();
        });
    }

    @Override
    public void onCountdownTick(long remainingSeconds) {
        SwingUtilities.invokeLater(() -> {
            long mins = remainingSeconds / 60;
            long secs = remainingSeconds % 60;
            String timeStr = String.format("%02d:%02d", mins, secs);
            pollCountdownLabel.setText("⏱️ זמן שנותר: " + timeStr);

            // Change color when time is low
            if (remainingSeconds <= 60) {
                pollCountdownLabel.setForeground(DANGER_COLOR);
            } else if (remainingSeconds <= 120) {
                pollCountdownLabel.setForeground(WARNING_COLOR);
            } else {
                pollCountdownLabel.setForeground(HEADER_COLOR);
            }
        });
    }

    @Override
    public void onScheduleCountdownTick(long remainingSeconds) {
        SwingUtilities.invokeLater(() -> {
            long mins = remainingSeconds / 60;
            long secs = remainingSeconds % 60;
            String timeStr = String.format("%02d:%02d", mins, secs);
            scheduleCountdownLabel.setText("⏱️ הסקר יישלח בעוד: " + timeStr);
        });
    }

    // ══════════════════════════════════════════
    // HELPER: Refresh poll table
    // ══════════════════════════════════════════

    private void refreshPollTable(Poll poll) {
        pollTableModel.setRowCount(0);
        for (User participant : poll.getParticipants().values()) {
            pollTableModel.addRow(new Object[]{
                participant.getFirstName(),
                poll.getProgressString(participant.getChatId()),
                poll.getParticipantStatus(participant.getChatId())
            });
        }
    }

    private void updatePollStats(Poll poll) {
        int total = poll.getParticipantCount();
        int completed = poll.getCompletedCount();
        int remaining = total - completed;
        pollStatsLabel.setText(String.format(
            "📈 משתתפים: %d  |  השלימו: %d  |  טרם השלימו: %d",
            total, completed, remaining));
    }

    // ══════════════════════════════════════════
    // RESULTS DISPLAY
    // ══════════════════════════════════════════

    private void showResults(Poll poll) {
        resultsContentPanel.removeAll();

        for (int i = 0; i < poll.getQuestionCount(); i++) {
            Question q = poll.getQuestions().get(i);
            JPanel qPanel = new JPanel();
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBackground(new Color(245, 248, 255));
            qPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(HEADER_COLOR, 1, true),
                new EmptyBorder(10, 12, 10, 12)
            ));
            qPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

            // Question title
            JLabel qTitle = new JLabel("📌 שאלה " + (i + 1) + ": " + q.getText());
            qTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
            qTitle.setForeground(HEADER_COLOR);
            qTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            qPanel.add(qTitle);
            qPanel.add(Box.createVerticalStrut(8));

            // Results sorted by votes
            List<Map.Entry<String, Integer>> sorted = q.getResultsSorted();
            int totalVotes = q.getTotalVotes();

            for (int j = 0; j < sorted.size(); j++) {
                Map.Entry<String, Integer> entry = sorted.get(j);
                String optionText = entry.getKey();
                int voteCount = entry.getValue();
                double percent = totalVotes > 0 ? (voteCount * 100.0 / totalVotes) : 0;

                // Option label
                JPanel optPanel = new JPanel(new BorderLayout(5, 0));
                optPanel.setBackground(new Color(245, 248, 255));
                optPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
                optPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel optLabel = new JLabel(String.format("%s — %d קולות (%.0f%%)", optionText, voteCount, percent));
                optLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                optPanel.add(optLabel, BorderLayout.NORTH);

                // Progress bar
                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue((int) percent);
                bar.setStringPainted(true);
                bar.setString(String.format("%.0f%%", percent));
                bar.setFont(new Font("Segoe UI", Font.BOLD, 11));
                bar.setPreferredSize(new Dimension(0, 20));

                // Color gradient based on rank
                if (j == 0) {
                    bar.setForeground(SUCCESS_COLOR);
                } else if (j == 1) {
                    bar.setForeground(HEADER_COLOR);
                } else {
                    bar.setForeground(WARNING_COLOR);
                }

                optPanel.add(bar, BorderLayout.CENTER);
                qPanel.add(optPanel);
                qPanel.add(Box.createVerticalStrut(5));
            }

            // Total votes
            JLabel totalLabel = new JLabel("סה\"כ קולות: " + totalVotes);
            totalLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            totalLabel.setForeground(Color.GRAY);
            totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            qPanel.add(totalLabel);

            resultsContentPanel.add(qPanel);
            resultsContentPanel.add(Box.createVerticalStrut(15));
        }

        // Summary stats
        JPanel summaryPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        summaryPanel.setBackground(new Color(235, 245, 235));
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SUCCESS_COLOR, 1, true),
            new EmptyBorder(10, 10, 10, 10)
        ));
        summaryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        summaryPanel.add(createStatLabel("👥 משתתפים", String.valueOf(poll.getParticipantCount())));
        summaryPanel.add(createStatLabel("✅ השלימו", String.valueOf(poll.getCompletedCount())));
        summaryPanel.add(createStatLabel("❌ לא השלימו", String.valueOf(poll.getParticipantCount() - poll.getCompletedCount())));
        summaryPanel.add(createStatLabel("📝 שאלות", String.valueOf(poll.getQuestionCount())));

        resultsContentPanel.add(summaryPanel);

        resultsContentPanel.revalidate();
        resultsContentPanel.repaint();

        sendPollButton.setEnabled(true);
        activePollCardLayout.show(activePollCardPanel, "RESULTS");
    }

    private JLabel createStatLabel(String title, String value) {
        JLabel label = new JLabel("<html><center>" + title + "<br><b style='font-size:14pt'>" + value + "</b></center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return label;
    }
}
