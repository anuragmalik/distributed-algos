import java.rmi.Remote;
import java.rmi.RemoteException;
/**
 * Interface providing callback functionality for interaction with the client machine
 * @author Anurag Malik, am3926
 *
 */
public interface ClientInterface extends Remote {
	// push message onto client machine
	public void pushTrace(String trace) throws RemoteException;
	
	// push request file data and trace onto client machine
	public boolean pushFile(byte[] buffer, String trace, String fileName) throws RemoteException;
	
	// request host name of the client machine
	public String getAddress() throws RemoteException;
}
