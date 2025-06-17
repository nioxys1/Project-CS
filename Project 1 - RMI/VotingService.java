import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * VotingService interface defines the remote methods available for the voting system.
 * This includes both regular voter operations and administrative functions.
 */
public interface VotingService extends Remote {
    
    /**
     * Authenticates a user with the provided credentials.
     * @param username The username of the voter
     * @param password The password of the voter
     * @return true if authentication succeeds, false otherwise
     * @throws RemoteException if there's a communication error
     */
    boolean login(String username, String password) throws RemoteException;
    
    /**
     * Checks if a user has already voted.
     * @param username The username to check
     * @return true if the user has voted, false otherwise
     * @throws RemoteException if there's a communication error
     */
    boolean hasVoted(String username) throws RemoteException;
    
    /**
     * Submits a vote for a candidate.
     * @param username The username of the voter
     * @param candidate The candidate being voted for
     * @return Status message indicating success or failure
     * @throws RemoteException if there's a communication error
     */
    String vote(String username, String candidate) throws RemoteException;
    
    /**
     * Retrieves the current voting results.
     * @return Formatted string containing the results
     * @throws RemoteException if there's a communication error
     */
    String getResults() throws RemoteException;
    
    /**
     * Retrieves a list of all voters who have cast votes (Admin only).
     * @return List of usernames who have voted
     * @throws RemoteException if there's a communication error
     */
    List<String> listVoters() throws RemoteException;
    
    /**
     * Resets all votes in the system (Admin only).
     * @return true if reset was successful, false otherwise
     * @throws RemoteException if there's a communication error
     */
    boolean resetVotes() throws RemoteException;
}
