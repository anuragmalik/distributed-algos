import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ProcessInterface extends Remote {

	String getHostName() throws RemoteException;

	void sendMarker(int procId)throws RemoteException;

	void acceptDeposit(int tran, int procId)throws RemoteException;

}
