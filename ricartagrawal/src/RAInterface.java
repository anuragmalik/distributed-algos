import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Following interface provides declaration for methods of a process in a
 * distributed systems network.
 * 
 * @author Anurag Malik, am3926@rit.edu
 *
 */
public interface RAInterface extends Remote {

	// accept deposit from another process
	void acceptDeposit(int tran, VectorClock vectorClock, int procId) throws RemoteException;

	// request from entering CS
	void requestCS(int procId, VectorClock vectorClock, VectorClock vectorClock2) throws RemoteException;

	// reply after a CS request is received
	void replyCS(int procId) throws RemoteException;

	// receive replies from another processes
	void recieveReply(int procId) throws RemoteException;

}
