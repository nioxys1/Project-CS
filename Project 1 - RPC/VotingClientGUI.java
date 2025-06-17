import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public class VotingClientGUI extends JFrame {
    // UI Components
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel loginStatus;
    private JTextField regUsernameField;
    private JPasswordField regPasswordField;
    private JLabel regStatus;
    private ButtonGroup voteGroup;
    private JLabel voteStatus;
    private JTextArea resultsArea;
    private JLabel countdownLabel;
    private javax.swing.Timer countdownTimer;
    private JPanel adminPanel;
    private JTextArea adminArea;
    
    // Network Components
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;
    
    // Constants
    private static final LocalDateTime VOTING_DEADLINE = LocalDateTime.of(2025, 6, 18, 18, 0);
    private static final String ADMIN_USERNAME = "admin";
    
    // Style Components
    private final Font titleFont = new Font("SansSerif", Font.BOLD, 18);
    private final Font regularFont = new Font("SansSerif", Font.PLAIN, 14);
    private final Font monoFont = new Font("Monospaced", Font.PLAIN, 13);
    private final Color successColor = new Color(0, 100, 0);
    private final Color errorColor = new Color(180, 0, 0);
    private final Color infoColor = new Color(0, 0, 139);
    private final Color bgColor = new Color(240, 240, 245);

    public VotingClientGUI() {
        initializeUI();
        connectToServer();
    }

    private void initializeUI() {
        setTitle("Online Voting System");
        setSize(650, 550);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(bgColor);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(bgColor);

        setupLoginPanel();
        setupRegistrationPanel();
        setupVotePanel();
        setupResultsPanel();
        setupAdminPanel();

        add(mainPanel);
        setVisible(true);
    }

    // ==================== PANEL SETUP METHODS ====================

    private void setupLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("User Login"));
        panel.setBackground(bgColor);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(createStyledLabel("Username:"), gbc);
        
        gbc.gridy++;
        usernameField = new JTextField();
        styleTextField(usernameField);
        panel.add(usernameField, gbc);

        gbc.gridy++;
        panel.add(createStyledLabel("Password:"), gbc);
        
        gbc.gridy++;
        passwordField = new JPasswordField();
        styleTextField(passwordField);
        panel.add(passwordField, gbc);

        gbc.gridy++;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        buttonPanel.setBackground(bgColor);
        JButton loginBtn = createStyledButton("Login", 150, 30);
        JButton registerBtn = createStyledButton("Register", 150, 30);
        loginBtn.addActionListener(e -> attemptLogin());
        registerBtn.addActionListener(e -> cardLayout.show(mainPanel, "register"));
        buttonPanel.add(loginBtn);
        buttonPanel.add(registerBtn);
        panel.add(buttonPanel, gbc);

        gbc.gridy++;
        loginStatus = createStatusLabel();
        panel.add(loginStatus, gbc);

        mainPanel.add(panel, "login");
    }

    private void setupRegistrationPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("New User Registration"));
        panel.setBackground(bgColor);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(createStyledLabel("Choose a username:"), gbc);
        
        gbc.gridy++;
        regUsernameField = new JTextField();
        styleTextField(regUsernameField);
        panel.add(regUsernameField, gbc);

        gbc.gridy++;
        panel.add(createStyledLabel("Choose a password:"), gbc);
        
        gbc.gridy++;
        regPasswordField = new JPasswordField();
        styleTextField(regPasswordField);
        panel.add(regPasswordField, gbc);

        gbc.gridy++;
        JButton registerBtn = createStyledButton("Register", 200, 30);
        registerBtn.addActionListener(e -> registerUser());
        panel.add(registerBtn, gbc);

        gbc.gridy++;
        regStatus = createStatusLabel();
        panel.add(regStatus, gbc);

        mainPanel.add(panel, "register");
    }

    private void setupVotePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Cast Your Vote"));
        panel.setBackground(bgColor);

        // Countdown display
        countdownLabel = new JLabel("", SwingConstants.CENTER);
        countdownLabel.setFont(titleFont);
        countdownLabel.setForeground(infoColor);
        panel.add(countdownLabel, BorderLayout.NORTH);

        // Voting options
        JPanel votePanel = new JPanel(new GridLayout(3, 1, 10, 10));
        votePanel.setBackground(bgColor);
        voteGroup = new ButtonGroup();
        String[] candidates = {"Alice", "Bob", "Charlie"};
        for (String c : candidates) {
            JRadioButton rb = new JRadioButton(c);
            rb.setFont(regularFont);
            rb.setBackground(bgColor);
            voteGroup.add(rb);
            votePanel.add(rb);
        }

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(bgColor);
        centerPanel.add(createStyledLabel("Choose a candidate:"), BorderLayout.NORTH);
        centerPanel.add(votePanel, BorderLayout.CENTER);
        panel.add(centerPanel, BorderLayout.CENTER);

        // Vote button and status
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        bottomPanel.setBackground(bgColor);
        
        JButton voteBtn = createStyledButton("Submit Vote", 200, 35);
        voteBtn.addActionListener(e -> submitVote());
        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonWrapper.setBackground(bgColor);
        buttonWrapper.add(voteBtn);
        bottomPanel.add(buttonWrapper, BorderLayout.NORTH);
        
        voteStatus = createStatusLabel();
        JPanel statusWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusWrapper.setBackground(bgColor);
        statusWrapper.add(voteStatus);
        bottomPanel.add(statusWrapper, BorderLayout.SOUTH);
        
        panel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(panel, "vote");
        
        startCountdown();
    }

    private void setupResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Voting Results"));
        panel.setBackground(bgColor);

        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(monoFont);
        resultsArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(resultsArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);

        JButton backBtn = createStyledButton("Back to Login", 150, 30);
        backBtn.addActionListener(e -> {
            usernameField.setText("");
            passwordField.setText("");
            cardLayout.show(mainPanel, "login");
        });
        panel.add(backBtn, BorderLayout.SOUTH);

        mainPanel.add(panel, "results");
    }

    private void setupAdminPanel() {
        adminPanel = new JPanel(new BorderLayout(10, 10));
        adminPanel.setBorder(BorderFactory.createTitledBorder("Administration Panel"));
        adminPanel.setBackground(bgColor);

        JLabel header = new JLabel("Admin Panel", SwingConstants.CENTER);
        header.setFont(titleFont);
        header.setForeground(infoColor);
        adminPanel.add(header, BorderLayout.NORTH);

        adminArea = new JTextArea();
        adminArea.setEditable(false);
        adminArea.setFont(monoFont);
        adminArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(adminArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        adminPanel.add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonPanel.setBackground(bgColor);
        
        JButton refreshBtn = createStyledButton("Refresh Results", 150, 30);
        JButton votersBtn = createStyledButton("List Voters", 150, 30);
        JButton resetBtn = createStyledButton("Reset Votes", 150, 30);
        JButton logoutBtn = createStyledButton("Logout", 100, 30);

        refreshBtn.addActionListener(e -> refreshAdminData("RESULTS", "Live Results"));
        votersBtn.addActionListener(e -> refreshAdminData("LISTVOTERS", "Voters List"));
        
        resetBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this, 
                "<html><b>WARNING:</b> This will permanently delete all voting data!<br>Are you sure?</html>", 
                "Confirm Reset", 
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                refreshAdminData("RESETVOTES", "System Reset");
            }
        });

        logoutBtn.addActionListener(e -> {
            cardLayout.show(mainPanel, "login");
            usernameField.setText("");
            passwordField.setText("");
        });

        buttonPanel.add(refreshBtn);
        buttonPanel.add(votersBtn);
        buttonPanel.add(resetBtn);
        buttonPanel.add(logoutBtn);

        adminPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(adminPanel, "admin");
    }

    // ==================== CORE FUNCTIONALITY ====================

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            showErrorAndExit("Could not connect to server: " + e.getMessage());
        }
    }

    private void attemptLogin() {
        username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus(loginStatus, "Please enter both username and password.", errorColor);
            return;
        }

        try {
            sendCommand("LOGIN " + username + " " + password);
            String response = in.readLine();
            if ("OK".equals(response)) {
                if (username.equals(ADMIN_USERNAME)) {
                    cardLayout.show(mainPanel, "admin");
                    refreshAdminData("RESULTS", "Welcome Admin");
                } else {
                    sendCommand("HASVOTED " + username);
                    String hasVoted = in.readLine();
                    if ("YES".equals(hasVoted)) {
                        showMessage("You have already voted. Showing results.");
                        showResults();
                    } else {
                        cardLayout.show(mainPanel, "vote");
                    }
                }
            } else {
                setStatus(loginStatus, "Login failed: " + response, errorColor);
            }
        } catch (IOException e) {
            showErrorAndExit("Error during login: " + e.getMessage());
        }
    }

    private void registerUser() {
        String newUser = regUsernameField.getText().trim();
        String newPass = new String(regPasswordField.getPassword()).trim();

        if (newUser.isEmpty() || newPass.isEmpty()) {
            setStatus(regStatus, "Please enter both fields.", errorColor);
            return;
        }

        try {
            File file = new File("users.txt");
            if (!file.exists()) file.createNewFile();

            java.util.List<String> existing = Files.readAllLines(file.toPath());
            for (String line : existing) {
                if (line.split(":")[0].equalsIgnoreCase(newUser)) {
                    setStatus(regStatus, "Username already exists.", errorColor);
                    return;
                }
            }

            String hashed = hashPassword(newPass);
            Files.write(file.toPath(), (newUser + ":" + hashed + "\n").getBytes(), StandardOpenOption.APPEND);
            showMessage("Registration successful! You can now log in.");
            cardLayout.show(mainPanel, "login");

        } catch (IOException e) {
            showErrorAndExit("Registration failed: " + e.getMessage());
        }
    }

    private void submitVote() {
        Enumeration<AbstractButton> buttons = voteGroup.getElements();
        String selected = null;
        while (buttons.hasMoreElements()) {
            JRadioButton b = (JRadioButton) buttons.nextElement();
            if (b.isSelected()) {
                selected = b.getText();
                break;
            }
        }

        if (selected == null) {
            setStatus(voteStatus, "Please select a candidate.", errorColor);
            return;
        }

        try {
            sendCommand("VOTE " + username + " " + selected);
            String response = in.readLine();

            if ("VOTING_CLOSED".equalsIgnoreCase(response)) {
                showMessage("Voting is now closed. You can no longer vote.");
                showResults();
                return;
            } else if ("OK".equalsIgnoreCase(response)) {
                setStatus(voteStatus, "Vote submitted successfully!", successColor);
                showResults();
            } else {
                setStatus(voteStatus, "Error: " + response, errorColor);
            }

        } catch (IOException e) {
            showErrorAndExit("Error during voting: " + e.getMessage());
        }
    }

    private void showResults() {
        try {
            sendCommand("RESULTS");
            String results = in.readLine();
            resultsArea.setText("Voting Results:\n" + formatServerResponse(results));
            cardLayout.show(mainPanel, "results");
        } catch (IOException e) {
            showErrorAndExit("Error fetching results: " + e.getMessage());
        }
    }

    private void refreshAdminData(String command, String title) {
        try {
            sendCommand(command);
            String response = in.readLine();
            adminArea.setText(title + ":\n" + formatServerResponse(response));
        } catch (IOException ex) {
            adminArea.setText("Error: " + ex.getMessage());
        }
    }

    // ==================== UTILITY METHODS ====================

    private void startCountdown() {
        countdownTimer = new javax.swing.Timer(1000, e -> updateCountdown());
        countdownTimer.start();
    }

    private void updateCountdown() {
        Duration remaining = Duration.between(LocalDateTime.now(), VOTING_DEADLINE);
        if (!remaining.isNegative()) {
            long days = remaining.toDays();
            long hours = remaining.toHoursPart();
            long minutes = remaining.toMinutesPart();
            long seconds = remaining.toSecondsPart();
            
            if (days > 0) {
                countdownLabel.setText(String.format("Voting closes in: %d days %02d:%02d:%02d", 
                    days, hours, minutes, seconds));
            } else {
                countdownLabel.setText(String.format("Voting closes in: %02d:%02d:%02d", 
                    hours, minutes, seconds));
            }
        } else {
            countdownLabel.setText("Voting is now closed!");
            countdownLabel.setForeground(errorColor);
            countdownTimer.stop();
        }
    }

    private String formatServerResponse(String response) {
        return response.replace("RESULTS ", "")
                      .replace("VOTERS ", "")
                      .replace(" ", "\n")
                      .replace(":", ": ");
    }

    private void sendCommand(String cmd) throws IOException {
        out.write(cmd);
        out.newLine();
        out.flush();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    // ==================== UI HELPER METHODS ====================

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(regularFont);
        return label;
    }

    private JLabel createStatusLabel() {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setFont(regularFont);
        return label;
    }

    private JButton createStyledButton(String text, int width, int height) {
        JButton button = new JButton(text);
        button.setFont(regularFont);
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        return button;
    }

    private void styleTextField(JComponent field) {
        field.setFont(regularFont);
        field.setPreferredSize(new Dimension(250, 30));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    private void setStatus(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showErrorAndExit(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VotingClientGUI client = new VotingClientGUI();
            client.setVisible(true);
        });
    }
}
