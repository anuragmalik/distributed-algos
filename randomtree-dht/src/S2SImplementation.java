import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * S2SImplementation provides implementation of the {@link S2SInterface} It
 * provides methods for forwarding a file search request or inserting a file
 * onto one server from another server.
 * 
 * @author Anurag Malik, am3926
 *
 */
public class S2SImplementation extends UnicastRemoteObject implements S2SInterface {

	private static final long serialVersionUID = 1L;
	private DHTServer server;
	private int[] serverNode;

	public S2SImplementation(DHTServer server) throws RemoteException {
		super();
		this.server = server;
		serverNode = new int[2];
	}

	/**
	 * Update coordinates for current server from details fetched from request
	 * packet
	 * 
	 * @param node
	 */
	private void setNode(int[] node) {
		int i = 0;
		while (i < 2)
			serverNode[i] = node[i++];

	}

	/**
	 * Return coordinates of the child nodes for this server node
	 * 
	 * @return
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
	 * Return coordinates of the parent node for this server node
	 * 
	 * @return
	 */
	private int[] parentNode() {
		int x = serverNode[0] - 1;
		int y = serverNode[1] / 2;
		return new int[] { x, y };
	}

	/**
	 * Check if the server is a leaf node
	 * 
	 * @return
	 */
	private boolean isLeafNode() {
		return serverNode[0] >= 2 ? true : false;
	}

	/**
	 * Check if the server is a root node
	 * 
	 * @return
	 */
	private boolean isRootNode() {
		return serverNode[0] == 0 ? true : false;
	}

	@Override
	/*
	 * This method is used by one server to forward a file search and download
	 * request to another server. (non-Javadoc)
	 * 
	 * @see S2SInterface#forwardRequest(Request, Trace)
	 */
	public boolean forwardRequest(Request request, Trace trace) throws RemoteException {

		System.out.println("New request for file '" + request.getFileName() + "'");
		String fileName = request.getFileName();

		// update request packet destination details.
		setNode(request.getDestination());

		File file = server.getFile(fileName);
		if (file != null) {
			// if file is present in the default lookup directory then send it
			// to client
			server.sendFile(file, request, trace);

			// increase popularity count for this file and replicate it to child
			// nodes if the popularity is more then 4
			server.increasePopularityCount(fileName);
			int popCount = server.getFilePopularity(fileName);
			System.out.println("Current popularity count :" + popCount);
			if (!isLeafNode() && popCount >= 5) {
				server.replicateFile(file, getChildNodes());
			}
			return true;
		} else if (!isRootNode()) {
			int[] parentNode = parentNode();
			request.setDestination(parentNode);

			// if file is not found then forward request to parent server
			return server.forwardRequest("" + parentNode[0] + parentNode[1], request, trace);
		} else {

			// Current server is root node, thus return trace to client.
			request.getClient().pushTrace(trace.getTrace());
		}
		return false;
	}

	@Override
	/*
	 * This method allows (non-Javadoc)
	 * 
	 * @see S2SInterface#insertFile(byte[], java.lang.String)
	 */
	public void insertFile(byte[] data, String fileName) throws RemoteException {
		server.fileInsert(data, fileName);
	}

	@Override
	public String getHostName() throws RemoteException {
		return server.getHostName();
	}

}
