import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.rmi.Naming;
import java.security.MessageDigest;
import java.time.*;
import java.util.List;
import java.util.*;

public class VotingClientRMI extends JFrame {
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
    private static final LocalDateTime VOTING_DEADLINE = LocalDateTime.of(2025, 6, 18, 18, 0);

    private VotingService service;
    private String username;

    public VotingClientRMI() {
        setTitle("Online Voting System (RMI)");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            service = (VotingService) Naming.lookup("rmi://localhost/VotingService");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to server.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        setupLoginPanel();
        setupRegistrationPanel();
        setupVotePanel();
        setupResultsPanel();
        setupAdminPanel();

        add(mainPanel);
        setVisible(true);
    }

    private void setupLoginPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> attemptLogin());

        JButton goToRegisterBtn = new JButton("Register");
        goToRegisterBtn.addActionListener(e -> cardLayout.show(mainPanel, "register"));

        JPanel btnPanel = new JPanel();
        btnPanel.add(loginBtn);
        btnPanel.add(goToRegisterBtn);
        panel.add(btnPanel);

        loginStatus = new JLabel("", SwingConstants.CENTER);
        panel.add(loginStatus);

        mainPanel.add(panel, "login");
    }

    private void setupRegistrationPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        panel.add(new JLabel("Choose a username:"));
        regUsernameField = new JTextField();
        panel.add(regUsernameField);

        panel.add(new JLabel("Choose a password:"));
        regPasswordField = new JPasswordField();
        panel.add(regPasswordField);

        JButton registerBtn = new JButton("Register");
        registerBtn.addActionListener(e -> registerUser());
        panel.add(registerBtn);

        regStatus = new JLabel("", SwingConstants.CENTER);
        panel.add(regStatus);

        mainPanel.add(panel, "register");
    }

    private void setupVotePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        countdownLabel = new JLabel("", SwingConstants.CENTER);
        panel.add(countdownLabel, BorderLayout.NORTH);

        JPanel votePanel = new JPanel(new GridLayout(3, 1, 5, 5));
        voteGroup = new ButtonGroup();
        for (String c : new String[]{"Alice", "Bob", "Charlie"}) {
            JRadioButton rb = new JRadioButton(c);
            voteGroup.add(rb);
            votePanel.add(rb);
        }
        panel.add(votePanel, BorderLayout.CENTER);

        voteStatus = new JLabel("", SwingConstants.CENTER);
        panel.add(voteStatus, BorderLayout.SOUTH);

        JButton voteBtn = new JButton("Submit Vote");
        voteBtn.addActionListener(e -> submitVote());
        panel.add(voteBtn, BorderLayout.EAST);

        mainPanel.add(panel, "vote");
        startCountdown();
    }

    private void setupResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        panel.add(new JScrollPane(resultsArea), BorderLayout.CENTER);

        JButton backBtn = new JButton("Back to Login");
        backBtn.addActionListener(e -> cardLayout.show(mainPanel, "login"));
        panel.add(backBtn, BorderLayout.SOUTH);

        mainPanel.add(panel, "results");
    }

    private void setupAdminPanel() {
        adminPanel = new JPanel(new BorderLayout());
        adminArea = new JTextArea();
        adminArea.setEditable(false);
        adminPanel.add(new JScrollPane(adminArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton refresh = new JButton("Refresh Results");
        refresh.addActionListener(e -> refreshAdminResults());
        JButton voters = new JButton("List Voters");
        voters.addActionListener(e -> listVoters());
        JButton reset = new JButton("Reset Votes");
        reset.addActionListener(e -> resetVotes());
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> cardLayout.show(mainPanel, "login"));
        buttons.add(refresh);
        buttons.add(voters);
        buttons.add(reset);
        buttons.add(logout);

        adminPanel.add(buttons, BorderLayout.SOUTH);
        mainPanel.add(adminPanel, "admin");
    }

    private void attemptLogin() {
        username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        try {
            if (service.login(username, password)) {
                if (username.equals("admin")) {
                    cardLayout.show(mainPanel, "admin");
                } else if (service.hasVoted(username)) {
                    JOptionPane.showMessageDialog(this, "You already voted.");
                    showResults();
                } else {
                    cardLayout.show(mainPanel, "vote");
                }
            } else {
                loginStatus.setText("Login failed.");
            }
        } catch (Exception e) {
            loginStatus.setText("Error: " + e.getMessage());
        }
    }

    private void registerUser() {
        String user = regUsernameField.getText().trim();
        String pass = new String(regPasswordField.getPassword());
        try {
            File file = new File("users.txt");
            if (!file.exists()) file.createNewFile();
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                if (line.split(":")[0].equals(user)) {
                    regStatus.setText("User exists.");
                    return;
                }
            }
            String hash = hash(pass);
            Files.write(file.toPath(), Collections.singletonList(user + ":" + hash), StandardOpenOption.APPEND);
            JOptionPane.showMessageDialog(this, "Registered.");
            cardLayout.show(mainPanel, "login");
        } catch (Exception e) {
            regStatus.setText("Error registering user.");
        }
    }

    private void submitVote() {
        try {
            Enumeration<AbstractButton> buttons = voteGroup.getElements();
            String selected = null;
            while (buttons.hasMoreElements()) {
                AbstractButton b = buttons.nextElement();
                if (b.isSelected()) selected = b.getText();
            }
            if (selected == null) {
                voteStatus.setText("Select a candidate.");
                return;
            }
            String res = service.vote(username, selected);
            voteStatus.setText("Vote: " + res);
            if ("OK".equalsIgnoreCase(res)) showResults();
        } catch (Exception e) {
            voteStatus.setText("Error: " + e.getMessage());
        }
    }

    private void showResults() {
        try {
            resultsArea.setText("Results:\n" + service.getResults());
            cardLayout.show(mainPanel, "results");
        } catch (Exception e) {
            resultsArea.setText("Error loading results.");
        }
    }

    private void refreshAdminResults() {
        try {
            adminArea.setText("Results:\n" + service.getResults());
        } catch (Exception e) {
            adminArea.setText("Error: " + e.getMessage());
        }
    }

    private void listVoters() {
        try {
            List<String> voters = service.listVoters();
            adminArea.setText("Voters:\n" + String.join("\n", voters));
        } catch (Exception e) {
            adminArea.setText("Error: " + e.getMessage());
        }
    }

    private void resetVotes() {
        try {
            if (service.resetVotes()) {
                adminArea.setText("Votes reset.");
            } else {
                adminArea.setText("Failed to reset votes.");
            }
        } catch (Exception e) {
            adminArea.setText("Error: " + e.getMessage());
        }
    }

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private void startCountdown() {
        countdownTimer = new javax.swing.Timer(1000, e -> updateCountdown());
        countdownTimer.start();
    }

    private void updateCountdown() {
        Duration d = Duration.between(LocalDateTime.now(), VOTING_DEADLINE);
        if (!d.isNegative()) {
            long h = d.toHours();
            long m = d.toMinutesPart();
            long s = d.toSecondsPart();
            countdownLabel.setText(String.format("Voting closes in: %02d:%02d:%02d", h, m, s));
        } else {
            countdownLabel.setText("Voting is now closed.");
            countdownTimer.stop();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(VotingClientRMI::new);
    }
}
