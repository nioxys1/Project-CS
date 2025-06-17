import java.io.*;
import java.nio.file.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

public class VotingServerRMI extends UnicastRemoteObject implements VotingService {
    private static final String USERS_FILE = "users.txt";
    private static final String VOTES_FILE = "votes.txt";
    private static final LocalDateTime VOTING_DEADLINE = LocalDateTime.of(2025, 6, 18, 18, 0);

    private final Map<String, String> users = new HashMap<>();
    private final Map<String, String> votes = new HashMap<>();
    private final Map<String, Integer> results = new HashMap<>();

    protected VotingServerRMI() throws RemoteException {
        super();
        loadUsers();
        loadVotes();
    }

    // Remote Methods
    public synchronized boolean login(String username, String password) throws RemoteException {
        reloadUsers(); // in case new users were registered
        String hashed = hashPassword(password);
        return users.containsKey(username) && users.get(username).equals(hashed);
    }

    public synchronized boolean hasVoted(String username) throws RemoteException {
        return votes.containsKey(username);
    }

    public synchronized String vote(String username, String candidate) throws RemoteException {
        if (LocalDateTime.now().isAfter(VOTING_DEADLINE)) {
            return "VOTING_CLOSED";
        }
        if (votes.containsKey(username)) {
            return "ALREADY_VOTED";
        }
        votes.put(username, candidate);
        results.put(candidate, results.getOrDefault(candidate, 0) + 1);
        appendVoteToFile(username, candidate);
        return "OK";
    }

    public synchronized String getResults() throws RemoteException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : results.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    // Admin-only methods
    public synchronized List<String> listVoters() throws RemoteException {
        return new ArrayList<>(votes.keySet());
    }

    public synchronized boolean resetVotes() throws RemoteException {
        votes.clear();
        results.clear();
        try {
            Files.deleteIfExists(Paths.get(VOTES_FILE));
            Files.createFile(Paths.get(VOTES_FILE));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Utility methods
    private void loadUsers() {
        try {
            Path path = Paths.get(USERS_FILE);
            if (!Files.exists(path)) return;
            for (String line : Files.readAllLines(path)) {
                String[] parts = line.split(":");
                if (parts.length == 2)
                    users.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            System.err.println("Failed to load users: " + e.getMessage());
        }
    }

    private void reloadUsers() {
        users.clear();
        loadUsers();
    }

    private void loadVotes() {
        try {
            Path path = Paths.get(VOTES_FILE);
            if (!Files.exists(path)) return;
            for (String line : Files.readAllLines(path)) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String user = parts[0];
                    String candidate = parts[1];
                    votes.put(user, candidate);
                    results.put(candidate, results.getOrDefault(candidate, 0) + 1);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load votes: " + e.getMessage());
        }
    }

    private void appendVoteToFile(String user, String candidate) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(VOTES_FILE, true))) {
            writer.write(user + ":" + candidate);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write vote: " + e.getMessage());
        }
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

    public static void main(String[] args) {
        try {
            // Start RMI registry on default port (1099)
           // LocateRegistry.createRegistry(1099);
            
            // Create and bind the server instance
            VotingServerRMI server = new VotingServerRMI();
            Naming.rebind("VotingService", server);
            
            System.out.println("VotingServerRMI ready.");
            System.out.println("Voting deadline: " + VOTING_DEADLINE);
        } catch (Exception e) {
            System.err.println("Server exception:");
            e.printStackTrace();
        }
    }
}
