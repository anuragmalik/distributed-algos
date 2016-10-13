import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * S2CInterface provides methods for interaction between client and servers,
 * 
 * @author Anurag Malik, am3926
 *
 */
public interface S2CInterface extends Remote {

	// search if a file is present on a server
	boolean searchFile(Request request, ClientInterface client) throws RemoteException;

	// upload a file onto server
	void insertFile(byte[] data, String fileName) throws RemoteException;

	// request a file to be searched and down loaded from server
	boolean requestFile(Request request) throws RemoteException;
}
