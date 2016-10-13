import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RaymondInterface provides the declarations of methods to be implemented for
 * supporting communication between multiple processes.
 * 
 * @author Anurag Malik, am3926@rit.edu
 */
public interface RaymondInterface extends Remote {
	// receive new request
	void receiveReq(int procId) throws RemoteException;
	// receive token
	void receiveToken(int procId) throws RemoteException;
}
