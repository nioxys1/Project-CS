import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VotingServerAES {
    private static final int PORT = 12345;
    private static final String USERS_FILE = "users.txt";
    private static final String LOG_FILE = "server.log";
    private static final Map<String, String> users = new ConcurrentHashMap<>();
    private static final Map<String, String> votes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> results = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        loadUsersFromFile(USERS_FILE);
        ServerSocket serverSocket = new ServerSocket(PORT);
        log("AES-encrypted voting server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            log("New encrypted connection from " + clientSocket.getInetAddress());
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void loadUsersFromFile(String filename) {
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
            log("Loaded " + users.size() + " users from file");
        } catch (IOException e) {
            log("Error loading users: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                AESUtil.getDecryptedInputStream(socket.getInputStream())));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                AESUtil.getEncryptedOutputStream(socket.getOutputStream())))) {
            
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line.trim());
                out.write(response);
                out.newLine();
                out.flush();
            }
        } catch (Exception e) {
            log("Encrypted connection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log("Error closing encrypted socket: " + e.getMessage());
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
                String voter = parts[1];
                String candidate = parts[2];
                if (votes.containsKey(voter)) {
                    log("VOTE attempt: " + voter + " already voted");
                    return "ALREADY_VOTED";
                }
                votes.put(voter, candidate);
                results.merge(candidate, 1, Integer::sum);
                log("VOTE: " + voter + " -> " + candidate);
                return "OK";

            case "RESULTS":
                StringBuilder sb = new StringBuilder("RESULTS ");
                for (Map.Entry<String, Integer> entry : results.entrySet()) {
                    sb.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
                }
                log("RESULTS requested");
                return sb.toString().trim();

            case "REGISTER":
                if (parts.length != 3) return "ERROR Invalid register format";
                String newUser = parts[1];
                String newPass = parts[2];
                if (users.containsKey(newUser)) {
                    log("REGISTER attempt: " + newUser + " already exists");
                    return "ERROR Username exists";
                }
                String newHashed = hashPassword(newPass);
                users.put(newUser, newHashed);
                saveUserToFile(newUser, newHashed);
                log("REGISTER: " + newUser + " SUCCESS");
                return "OK";

            default:
                return "ERROR Unknown command";
        }
    }

    private static void saveUserToFile(String username, String hashedPassword) {
        try {
            Files.write(Paths.get(USERS_FILE), 
                      (username + ":" + hashedPassword + "\n").getBytes(), 
                      StandardOpenOption.APPEND);
        } catch (IOException e) {
            log("Failed to save user: " + e.getMessage());
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
