import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface provideSs methods for interaction between two servers.
 * 
 * @author Anurag Malik, am3926
 *
 */
public interface S2SInterface extends Remote {

	// receive forwarded request from another server
	boolean forwardRequest(Request packet, Trace trace) throws RemoteException;

	// receive file data from another server
	void insertFile(byte[] data, String fileName) throws RemoteException;

	// return host name of the server machine
	String getHostName() throws RemoteException;
}
