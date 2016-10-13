import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ProcessInterface extends Remote {

	void acceptDeposit(int tran, VectorClock vectorClock, int procId) throws RemoteException;

	String getHostName() throws RemoteException;

}
