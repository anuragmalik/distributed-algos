import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * This class provides implementation of the {@link S2CInterface}. It provides
 * methods for interaction between a Server and a Client. It allows
 * functionality for file search, file download and file insertion.
 * 
 * @author Anurag Malik, am3926
 *
 */
public class S2CImplementation extends UnicastRemoteObject implements S2CInterface {
	private static final long serialVersionUID = 1L;
	private DHTServer server;
	private int[] serverNode;

	public S2CImplementation(DHTServer server) throws RemoteException {
		super();
		this.server = server;
		serverNode = new int[2];
	}

	@Override
	/*
	 * This method provides functionality for the clients to search if a file
	 * exists on this server. (non-Javadoc)
	 * 
	 * @see S2CInterface#searchFile(Request, ClientInterface)
	 */
	public boolean searchFile(Request request, ClientInterface client) throws RemoteException {
		System.out.println("New request for file '" + request.getFileName() + "' from :" + client.getAddress());
		String fileName = request.getFileName();
		setNode(request.getDestination());

		File file = server.getFile(fileName);
		if (file != null) {
			return true;
		} else
			return false;

	}

	@Override
	/*
	 * This method allows a client to insert/ upload a file onto this server.
	 * (non-Javadoc)
	 * 
	 * @see S2CInterface#insertFile(byte[], java.lang.String)
	 */
	public void insertFile(byte[] data, String fileName) throws RemoteException {
		server.fileInsert(data, fileName);
	}

	/**
	 * After receiving a request packet, a server can fetch details about its
	 * position in the distributed system network.
	 * 
	 * @param node
	 */
	private void setNode(int[] node) {
		int i = 0;
		while (i < 2)
			serverNode[i] = node[i++];
	}

	/**
	 * This method return the coordinates of the child nodes for the current
	 * server.
	 * 
	 * @return child nodes coordinates
	 */
	private String[] getChildNodes() {
		String[] childNodes = new String[2];

		int x = serverNode[0] + 1;
		int y = serverNode[1] * 2;
		childNodes[0] = "" + x + y;
		childNodes[1] = "" + x + (y + 1);

		return childNodes;
	}

	/**
	 * This method return the coordinates of the parent node for the current
	 * node
	 * 
	 * @return coordinates of the parent node
	 */
	private int[] parentNode() {
		int x = serverNode[0] - 1;
		int y = (serverNode[1] - 1) / 2;
		return new int[] { x, y };
	}

	/**
	 * Check if the current node is leaf node
	 * 
	 * @return
	 */
	private boolean isLeafNode() {
		return serverNode[0] >= 2 ? true : false;
	}

	/**
	 * Check if the current node is root node
	 * 
	 * @return
	 */
	private boolean isRootNode() {
		return serverNode[0] == 0 ? true : false;
	}

	@Override
	/*
	 * This method is responsible for accepting file download requests from
	 * clients (non-Javadoc)
	 * 
	 * @see S2CInterface#requestFile(Request)
	 */
	public synchronized boolean requestFile(Request request) throws RemoteException {

		System.out.println(
				"New request for file '" + request.getFileName() + "' from : " + request.getClient().getAddress());
		Trace trace = new Trace();
		String fileName = request.getFileName();
		setNode(request.getDestination());

		// check if the requested file is available on server
		File file = server.getFile(fileName);
		if (file != null) {

			// if file is found, then send file to the client
			server.sendFile(file, request, trace);

			// increase popularity count of this file
			server.increasePopularityCount(fileName);

			// if popularity of file is more than or equal to 5, replicate this
			// file onto child servers.
			if (!isLeafNode() && server.getFilePopularity(fileName) >= 5) {
				server.replicateFile(file, getChildNodes());
			}
			return true;
		} else if (!isRootNode()) {

			// if file is not found on current server, then forward the request
			// onto parent node.
			int[] parentNode = parentNode();
			request.setDestination(parentNode);
			return server.forwardRequest("" + parentNode[0] + parentNode[1], request, trace);
		} else {

			// if current node is root, then send an error message to client
			request.getClient().pushTrace(trace.getTrace());
		}
		return false;
	}

}
