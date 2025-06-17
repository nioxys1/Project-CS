import java.io.*;                        // For input/output stream handling
import java.net.*;                       // For networking (Socket, ServerSocket)
import java.nio.file.*;                  // For file operations
import java.security.MessageDigest;      // For hashing passwords
import java.security.NoSuchAlgorithmException;
import java.time.*;                      // For managing deadlines and time left
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Thread-safe map for concurrent use

public class VotingServer {
    private static final int PORT = 12345; // Server port number
    private static final String USERS_FILE = "users.txt";   // File storing user credentials
    private static final String VOTES_FILE = "votes.txt";   // File storing votes
    private static final String LOG_FILE = "server.log";    // File for logging server activity
    private static final LocalDateTime VOTING_DEADLINE = LocalDateTime.of(2025, 6, 18, 18, 0); // Voting closes at this date/time

    // In-memory data structures
    private static final Map<String, String> users = new ConcurrentHashMap<>();   // Stores username -> hashed password
    private static final Map<String, String> votes = new ConcurrentHashMap<>();   // Stores username -> candidate voted
    private static final Map<String, Integer> results = new ConcurrentHashMap<>();// Stores candidate -> vote count

    public static void main(String[] args) throws IOException {
        loadUsersFromFile(USERS_FILE);   // Load users from file at startup
        loadVotesFromFile(VOTES_FILE);   // Load votes if already present

        ServerSocket serverSocket = new ServerSocket(PORT); // Start server socket
        log("Server started on port " + PORT + " | Voting deadline: " + VOTING_DEADLINE);

        // Accept incoming client connections
        while (true) {
            Socket clientSocket = serverSocket.accept(); // Accept a new client
            log("New connection from " + clientSocket.getInetAddress());
            new Thread(() -> handleClient(clientSocket)).start(); // Handle each client in a new thread
        }
    }

    // Loads users from the file into the 'users' map
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
                    users.put(parts[0], parts[1]); // username:hashed_password
                }
            }
            log("Loaded " + users.size() + " users");
        } catch (IOException e) {
            log("Failed to load users: " + e.getMessage());
        }
    }

    // Loads votes from file into memory and populates the results
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
                    results.merge(candidate, 1, Integer::sum); // Count votes
                }
            }
            log("Loaded " + votes.size() + " votes");
        } catch (IOException e) {
            log("Failed to load votes: " + e.getMessage());
        }
    }

    // Save a new vote to the votes file
    private static void appendVoteToFile(String user, String candidate) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(VOTES_FILE, true))) {
            writer.write(user + ":" + candidate);
            writer.newLine();
            log("Saved vote: " + user + " -> " + candidate);
        } catch (IOException e) {
            log("Failed to save vote: " + e.getMessage());
        }
    }

    // Handle client communication
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

    // Process commands received from the client
    private static String processCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length == 0) return "ERROR Empty command";

        switch (parts[0].toUpperCase()) {

            case "LOGIN": // LOGIN username password
                if (parts.length != 3) return "ERROR Invalid login format";
                String user = parts[1];
                String pass = parts[2];
                String hashed = hashPassword(pass);
                loadUsersFromFile(USERS_FILE); // Reload to sync with file changes
                if (users.containsKey(user) && users.get(user).equals(hashed)) {
                    log("LOGIN: " + user + " SUCCESS");
                    return "OK";
                } else {
                    log("LOGIN: " + user + " FAILED");
                    return "ERROR Invalid credentials";
                }

            case "HASVOTED": // HASVOTED username
                if (parts.length != 2) return "ERROR Invalid format";
                return votes.containsKey(parts[1]) ? "YES" : "NO";

            case "VOTE": // VOTE username candidate
                if (parts.length != 3) return "ERROR Invalid vote format";
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

            case "RESULTS": // RESULTS
                StringBuilder sb = new StringBuilder("RESULTS ");
                for (Map.Entry<String, Integer> entry : results.entrySet()) {
                    sb.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
                }
                log("RESULTS requested");
                return sb.toString().trim();

            case "TIMELEFT": // TIMELEFT
                if (LocalDateTime.now().isAfter(VOTING_DEADLINE)) {
                    return "VOTING_CLOSED";
                }
                Duration remaining = Duration.between(LocalDateTime.now(), VOTING_DEADLINE);
                return String.format("TIME_LEFT %dd %dh %dm", 
                    remaining.toDays(), 
                    remaining.toHours() % 24, 
                    remaining.toMinutes() % 60);

            case "LISTVOTERS": // LISTVOTERS
                if (votes.isEmpty()) return "VOTERS none";
                StringBuilder voterList = new StringBuilder("VOTERS ");
                for (String name : votes.keySet()) {
                    voterList.append(name).append(" ");
                }
                return voterList.toString().trim();

            case "RESETVOTES": // RESETVOTES
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

    // Hash password using SHA-256
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
            return password; // Return unhashed (bad fallback, should be avoided in real apps)
        }
    }

    // Append message to server log file with timestamp
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
