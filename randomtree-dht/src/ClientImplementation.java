import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/***
 * This class represents implementation of ClientInterface. It is responsible
 * for providing methods required to retrieve messages or file data from server.
 * 
 * @author Anurag Malik, am3926
 *
 */
public class ClientImplementation extends UnicastRemoteObject implements ClientInterface {

	private Client client;
	static final long serialVersionUID = 1L;

	protected ClientImplementation() throws RemoteException {
		super();
	}

	protected ClientImplementation(Client client) throws RemoteException {
		super();
		this.client = client;
	}

	@Override
	/*
	 * Method used to insert messages to this client
	 * (non-Javadoc)
	 * @see ClientInterface#pushTrace(java.lang.String)
	 */
	public void pushTrace(String trace) throws RemoteException {
		System.out.println(trace);
	}

	@Override
	/*
	 * Method used by servers to push file data and trace message onto this client
	 * (non-Javadoc)
	 * @see ClientInterface#pushFile(byte[], java.lang.String, java.lang.String)
	 */
	public boolean pushFile(byte[] buffer, String trace, String fileName) throws RemoteException {

		System.out.println(trace);
		return client.fileInsert(buffer, fileName);
	}

	@Override
	/*
	 * Return host name of the client machine
	 * (non-Javadoc)
	 * @see ClientInterface#getAddress()
	 */
	public String getAddress() throws RemoteException {
		return client.getHostName();
	}
} // ClientLogic
