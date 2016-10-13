import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * BullyInterface provides unimplemented methods to be defined by each
 * BullyProcess implementing this interface.
 * 
 * @author Anurag Malik, am3926
 */
public interface BullyInterface extends Remote {

	// send election message
	public void election(int proc) throws RemoteException;

	// send leader coordination message
	public void leader(int proc) throws RemoteException;

	// send reply for a election request
	public void reply(int proc) throws RemoteException;

}
