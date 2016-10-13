import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;

/**
 * DHTServer represents a server capable of interacting with other similar
 * servers in a distributed systems setup. Each server can accept file search
 * and download request from other servers on network or from client.
 *
 * @author Anurag Malik, am3926
 *
 */
public class DHTServer extends Thread implements Serializable {

	private static final long serialVersionUID = 1L;
	static final int PORT = 4040;
	private HashMap<Integer, String> hashTable;
	private static HashMap<String, Integer> fileMap;
	private String lookupDirectory;
	private int TOTAL_SERVERS = 0;

	// Initialize hash map of all servers over network and default directory for
	// this server.
	public DHTServer() {
		hashTable = new HashMap<>();
		fileMap = new HashMap<>();
		lookupDirectory = System.getProperty("user.home") + "/Courses/dht/" + getHostName() + "/";
	}

	public void run() {
		initServer();
		execServer();
	}

	/**
	 * Initialize utility hash map with details of other servers available on
	 * network.
	 */
	public void initServer() {
		String[] servers = { "glados.cs.rit.edu", "kansas.cs.rit.edu", "gorgon.cs.rit.edu",
				"newyork.cs.rit.edu", "yes.cs.rit.edu", "kinks.cs.rit.edu", "medusa.cs.rit.edu", "joplin.cs.rit.edu",
				"delaware.cs.rit.edu", "buddy.cs.rit.edu", "arizona.cs.rit.edu" };
		TOTAL_SERVERS = servers.length;
		for (int i = 0; i < TOTAL_SERVERS; i++) {
			hashTable.put(i, servers[i]);
		}

	}

	/**
	 * Utility method to display details of all other servers connected on
	 * network.
	 */
	private void viewHashTable() {
		System.out.println("HASHTABLE :");
		for (String server : hashTable.values())
			System.out.println(server);

	}

	/**
	 * This method is responsible for setting up a default server interface to
	 * be made available over RMI, for other servers or clients to connect.
	 */
	public void execServer() {

		try {
			// export rmi instance for Server to Client interaction
			S2CInterface exportedObj = new S2CImplementation(this);

			// export rmi instance for Server to Server interaction
			S2SInterface serverInterface = new S2SImplementation(this);

			// bind exported instanced on RMI registry
			Registry registry = LocateRegistry.createRegistry(PORT);
			registry.rebind("dht", exportedObj);
			registry.rebind("server", serverInterface);
			System.out.println("Server Name : " + getHostName());
			System.out.println("Lookup directory : " + lookupDirectory);

		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}
	}

	/**
	 * Return host name for this server
	 * 
	 * @return host name of the current server.
	 */
	public String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * This method is used by a server for replicating a popular file to its
	 * neighbouring child servers.
	 * 
	 * @param file
	 *            : file to be replicated
	 * @param nodes
	 *            : nodes of all servers where file has to be replicated
	 */
	public void replicateFile(File file, String[] nodes) {
		S2SInterface server;
		int id = 0;
		String hostName = null;
		byte[] buffer = null;

		try {
			buffer = readFile(file);
			for (String child : nodes) {

				// find hostname for each child node and replicate file onto
				// them
				id = Math.abs((file.getName() + child).hashCode()) % TOTAL_SERVERS;
				System.out.println("@" + getHostName() + " - Connecting to : " + hashTable.get(id));
				hostName = "rmi://" + hashTable.get(id) + ":" + PORT + "/server";

				// find RMI interface to the child nodes
				server = (S2SInterface) Naming.lookup(hostName);
				System.out.println("File : " + file.getName() + " being copied to node : " + server.getHostName());
				server.insertFile(buffer, file.getName());
			}
		} catch (FileNotFoundException e) {
			System.out.println("Replication failed. File not found.");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method allows for a file to be inserted /downloaded into default
	 * lookup directory of a server.
	 * 
	 * @param data
	 *            : file data
	 * @param fileName
	 *            : name of file being downloaded
	 * @return True if file insertion is successful, False otherwise
	 */
	public boolean fileInsert(byte[] data, String fileName) {
		FileOutputStream fos;
		try {

			// write file in default lookup directory in server
			fos = new FileOutputStream(lookupDirectory + fileName);
			fos.write(data);
			fos.close();
			System.out.println("File Insertion successful.");
			fileMap.put(fileName, 0);
			return true;
		} catch (IOException e) {
			System.out.println("Failed to read input file.");
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Chech if a file exists in default lookup directory of a server.
	 * 
	 * @param fileName
	 *            : file being searched
	 * @return File instance if file is found.
	 */
	public File getFile(String fileName) {
		File file = new File(lookupDirectory + fileName);
		if (file.exists())
			return file;
		else
			return null;
	}

	/**
	 * Utility function to read a requested file from default lookup directory
	 * on a server
	 * 
	 * @param file
	 *            : file to be read
	 * @return bytes of file data
	 * @throws FileNotFoundException
	 */
	private byte[] readFile(File file) throws FileNotFoundException {
		if (!file.exists())
			throw new FileNotFoundException();

		byte buffer[] = new byte[(int) file.length()];
		try {

			// read data from file
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(file.getPath()));
			input.read(buffer, 0, buffer.length);
			input.close();
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		return (buffer);
	}

	/**
	 * This method is used to send a file to the client who requested it.
	 * 
	 * @param file
	 *            : file to be sent
	 * @param request
	 *            : request packet
	 * @param trace
	 *            : trace data for this request
	 */
	public void sendFile(File file, Request request, Trace trace) {

		ClientInterface client = request.getClient();
		try {

			// read file from server directory and send it to client
			byte[] buffer = readFile(file);
			trace.addToTrace(getHostName());
			trace.setStatus(true);

			// send file data along with whole trace of this request
			client.pushFile(buffer, trace.getTrace(), file.getName());
			
		} catch (Exception e) {
			System.out.println("File read & transfer error.");
			try {
				
				// jsut send the trace to the client
				client.pushTrace(trace.getTrace());
			} catch (RemoteException e1) {
				System.out.println("Lost connection with client. Exiting.");
				return;
			}
			return;
		}
	}

	/**
	 * This method is responsible for increasing the popularity count of a file.
	 * 
	 * @param fileName
	 *            : name of file whose popularity is to be updated
	 */
	public void increasePopularityCount(String fileName) {

		// check of this is a new file request
		if (fileMap.containsKey(fileName)) {
			int count = fileMap.get(fileName);
			fileMap.put(fileName, count + 1);
		} else
			fileMap.put(fileName, 1);
	}

	/**
	 * This method return the popularity count of a file.
	 * 
	 * @param fileName:
	 *            name of file to be checked in map
	 * @return popularity count
	 */

	public int getFilePopularity(String fileName) {
		if (fileMap.containsKey(fileName))
			return fileMap.get(fileName);
		else
			return 0;
	}

	/**
	 * If a file being requested from this server is not found, then forward
	 * request to another parent server.
	 * 
	 * @param parentNode
	 * @param request
	 * @param trace
	 * @return
	 */
	public boolean forwardRequest(String parentNode, Request request, Trace trace) {
		trace.addToTrace(getHostName());
		try {
			// connect to another server over network and forward file search
			// request to it.
			int id = Math.abs((request.getFileName() + parentNode).hashCode()) % TOTAL_SERVERS;
			System.out.println("@" + getHostName() + " - Connecting to : " + hashTable.get(id));
			String hostName = "rmi://" + hashTable.get(id) + ":" + PORT + "/server";

			// RMI lookup for required host name
			S2SInterface server = (S2SInterface) Naming.lookup(hostName);
			System.out
					.println("Forwarding " + request.getFileName() + " request to parent server: " + hashTable.get(id));
			return server.forwardRequest(request, trace);

		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			System.out.println("Failed to connect to parent node.");
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Execution starts from the main method which is responsible for starting a
	 * server and make it available for file request and search from other
	 * servers/clients.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		DHTServer server = new DHTServer();
		server.start();
	}

}
