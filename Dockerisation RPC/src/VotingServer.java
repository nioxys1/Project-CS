import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VotingServer {
    private static final int PORT = 12345;
    private static final String USERS_FILE = "users.txt";
    private static final String VOTES_FILE = "votes.txt";
    private static final String LOG_FILE = "server.log";
    private static final LocalDateTime VOTING_DEADLINE = LocalDateTime.of(2025, 6, 18, 18, 0); // June 1, 2025 at 6:00 PM
    
    private static final Map<String, String> users = new ConcurrentHashMap<>();
    private static final Map<String, String> votes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> results = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        loadUsersFromFile(USERS_FILE);
        loadVotesFromFile(VOTES_FILE);

        ServerSocket serverSocket = new ServerSocket(PORT);
        log("Server started on port " + PORT + " | Voting deadline: " + VOTING_DEADLINE);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            log("New connection from " + clientSocket.getInetAddress());
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void loadUsersFromFile(String filename) {
        users.clear();
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                Files.createFile(path);
                log("Created new users file");
                return;
            }
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                }
            }
            log("Loaded " + users.size() + " users");
        } catch (IOException e) {
            log("Failed to load users: " + e.getMessage());
        }
    }

    private static void loadVotesFromFile(String filename) {
        votes.clear();
        results.clear();
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                Files.createFile(path);
                log("Created new votes file");
                return;
            }
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String user = parts[0];
                    String candidate = parts[1];
                    votes.put(user, candidate);
                    results.merge(candidate, 1, Integer::sum);
                }
            }
            log("Loaded " + votes.size() + " votes");
        } catch (IOException e) {
            log("Failed to load votes: " + e.getMessage());
        }
    }

    private static void appendVoteToFile(String user, String candidate) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(VOTES_FILE, true))) {
            writer.write(user + ":" + candidate);
            writer.newLine();
            log("Saved vote: " + user + " -> " + candidate);
        } catch (IOException e) {
            log("Failed to save vote: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line.trim());
                out.write(response);
                out.newLine();
                out.flush();
            }
        } catch (IOException e) {
            log("Connection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log("Error closing socket: " + e.getMessage());
            }
        }
    }

    private static String processCommand(String command) {
    String[] parts = command.split(" ");
    if (parts.length == 0) return "ERROR Empty command";

    switch (parts[0].toUpperCase()) {
        case "LOGIN":
            if (parts.length != 3) return "ERROR Invalid login format";
            String user = parts[1];
            String pass = parts[2];
            String hashed = hashPassword(pass);
            loadUsersFromFile(USERS_FILE);
            if (users.containsKey(user) && users.get(user).equals(hashed)) {
                log("LOGIN: " + user + " SUCCESS");
                return "OK";
            } else {
                log("LOGIN: " + user + " FAILED");
                return "ERROR Invalid credentials";
            }

        case "HASVOTED":
            if (parts.length != 2) return "ERROR Invalid format";
            return votes.containsKey(parts[1]) ? "YES" : "NO";

        case "VOTE":
            if (parts.length != 3) return "ERROR Invalid vote format";
            
            // Check voting deadline
            if (LocalDateTime.now().isAfter(VOTING_DEADLINE)) {
                Duration lateBy = Duration.between(VOTING_DEADLINE, LocalDateTime.now());
                log("VOTE attempt " + lateBy.toMinutes() + " minutes after deadline");
                return "VOTING_CLOSED Deadline was " + VOTING_DEADLINE.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            String voter = parts[1];
            String candidate = parts[2];
            if (votes.containsKey(voter)) {
                log("VOTE attempt: " + voter + " already voted");
                return "ALREADY_VOTED";
            }
            votes.put(voter, candidate);
            results.merge(candidate, 1, Integer::sum);
            appendVoteToFile(voter, candidate);
            log("VOTE: " + voter + " -> " + candidate);
            return "OK";

        case "RESULTS":
            StringBuilder sb = new StringBuilder("RESULTS ");
            for (Map.Entry<String, Integer> entry : results.entrySet()) {
                sb.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
            }
            log("RESULTS requested");
            return sb.toString().trim();

        case "TIMELEFT":
            if (LocalDateTime.now().isAfter(VOTING_DEADLINE)) {
                return "VOTING_CLOSED";
            }
            Duration remaining = Duration.between(LocalDateTime.now(), VOTING_DEADLINE);
            return String.format("TIME_LEFT %dd %dh %dm", 
                remaining.toDays(), 
                remaining.toHours() % 24, 
                remaining.toMinutes() % 60);

        case "LISTVOTERS":
            if (votes.isEmpty()) return "VOTERS none";
            StringBuilder voterList = new StringBuilder("VOTERS ");
            for (String name : votes.keySet()) {
                voterList.append(name).append(" ");
            }
            return voterList.toString().trim();

        case "RESETVOTES":
            votes.clear();
            results.clear();
            try {
                Files.deleteIfExists(Paths.get(VOTES_FILE));
                Files.createFile(Paths.get(VOTES_FILE));
            } catch (IOException e) {
                log("Reset error: " + e.getMessage());
                return "RESET_FAILED";
            }
            log("Votes reset by admin");
            return "RESET_OK";

        default:
            return "ERROR Unknown command";
    }
}

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log("Password hashing error: " + e.getMessage());
            return password;
        }
    }

    private static void log(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Log error: " + e.getMessage());
        }
    }
}
