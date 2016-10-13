import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

/***
 * This class represents a client capable of connecting to servers within a
 * distributed systems network. Client can upload or download file from servers.
 * 
 * @author Anurag Malik, am3926
 */
public class Client implements Remote {

	static HashMap<Integer, String> hashTable = null;
	private String lookupDirectory;
	private static int TOTAL_SERVERS = 0;
	private int PORT = 4040;

	public Client() {
		// initialize lookup directory for client and initialize hashmap for
		// available servers.
		hashTable = new HashMap<>();
		lookupDirectory = System.getProperty("user.home") + "/Courses/dht/Client/";
		initClient();
	}

	public void initClient() {

		// list of all available servers
		String[] servers = { "glados.cs.rit.edu", "kansas.cs.rit.edu", "gorgon.cs.rit.edu",
				"newyork.cs.rit.edu", "yes.cs.rit.edu", "kinks.cs.rit.edu", "medusa.cs.rit.edu", "joplin.cs.rit.edu",
				"delaware.cs.rit.edu", "buddy.cs.rit.edu", "arizona.cs.rit.edu" };
		TOTAL_SERVERS = servers.length;
		for (int i = 0; i < TOTAL_SERVERS; i++) {
			hashTable.put(i, servers[i]);
		}

		System.out.println("@Client : Lookup directory - " + lookupDirectory);
	} // initClient

	/**
	 * Method to view all hash table entries.
	 */
	private void viewHashTable() {
		System.out.println("HASHTABLE :");
		for (String server : hashTable.values())
			System.out.println("\t" + server);
		System.out.println();
	} // viewHashTable

	/**
	 * Return host name of the client machine
	 * 
	 * @return Machine host-name
	 */
	public String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	} // getHostName

	/**
	 * this method is responsible for reading file data from the default lookup
	 * directory on client machine and return its byte data.
	 * 
	 * @param fileName
	 *            : name of file to be read
	 * @return byte data read from file
	 * @throws FileNotFoundException
	 */
	public byte[] readFile(String fileName) throws FileNotFoundException {

		// lookup for file in default lookup directory
		File file = new File(lookupDirectory + fileName);
		if (!file.exists())
			throw new FileNotFoundException();

		// read data into byte array
		byte buffer[] = new byte[(int) file.length()];
		try {
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(file.getPath()));
			input.read(buffer, 0, buffer.length);
			input.close();
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		return buffer;
	} // readFile

	/**
	 * This method is responsible for connecting to any random servers on a
	 * distributed systems network and upload a given file to the server.
	 * 
	 * @param fileName
	 *            : file to be uploaded
	 */
	public void sendToServer(String fileName) {
		try {
			byte[] data = readFile(fileName);

			// calculate the root node for given file
			int id = Math.abs((fileName + "00").hashCode()) % TOTAL_SERVERS;
			System.out.println("Sending file to : " + hashTable.get(id));

			// naming lookup for the required server, get host-name from hashmap
			String registryURL = "rmi://" + hashTable.get(id) + ":" + PORT + "/dht";
			S2CInterface server = (S2CInterface) Naming.lookup(registryURL);

			// connection successful, upload file to server
			server.insertFile(data, fileName);

		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			System.out.println("Error : Unable to establish connection with server.");
			// e.printStackTrace();
		} catch (FileNotFoundException e) {
			System.out.println("Error : File reading error.\nFile not found.");
			// e.printStackTrace();
		}
	} // sendToServer

	/**
	 * This method is responsible for accepting file data from a server after a
	 * download request has been made for a file and it is found of any of the
	 * servers.
	 * 
	 * @param data
	 *            : data of the file requested
	 * @param fileName
	 *            : name of file to be searched on servers
	 * @return true if file is successfully down-loaded, false otherwise
	 */
	public boolean fileInsert(byte[] data, String fileName) {
		FileOutputStream fos;
		try {
			// write the file data into default lookup directory
			fos = new FileOutputStream(lookupDirectory + fileName);
			fos.write(data);
			fos.close();
			System.out.println("File Insertion successful.");
			return true;
		} catch (IOException e) {
			System.out.println("Failed to read input file.");
			e.printStackTrace();
		}
		return false;
	} // fileInsert

	/**
	 * This method is called to search and request for downloading a file from
	 * servers.
	 * 
	 * @param client
	 *            : reference of the client instance making download request.
	 * @param fileName
	 *            : name of the file being request
	 * @throws RemoteException
	 * @throws NotBoundException
	 * @throws MalformedURLException
	 */
	private static void downloadData(Client client, String fileName)
			throws RemoteException, NotBoundException, MalformedURLException {

		// create a callback client instance, servers will reply/communicate
		// using this instance
		ClientInterface callBack = new ClientImplementation(client);

		// create a new request packet, including all details
		// including file being requested, client callback instance
		Request packet = new Request();
		packet.setFileName(fileName);
		packet.setClient(callBack);

		// get random leaf node on distributed systems network
		int[] serverNode = getServerNode();
		packet.setDestination(serverNode);

		// get server host-name from Hashmap and connect to its RMI interface.
		int id = Math.abs((fileName + serverNode[0] + serverNode[1]).hashCode()) % TOTAL_SERVERS;
		System.out.println("@Client - Connecting to : " + hashTable.get(id));
		String registryURL = "rmi://" + hashTable.get(id) + ":" + client.PORT + "/dht";
		S2CInterface server = (S2CInterface) Naming.lookup(registryURL);

		// request file from the server
		server.requestFile(packet);
	} // downloadData

	/**
	 * This method return a random leaf node co-ordinates within a distributed
	 * systems network.
	 * 
	 * @return Coordinates of a random server
	 */
	private static int[] getServerNode() {
		return new int[] { 2, new Random().nextInt() % 4 };
	} // getServerNode

	/**
	 * Execution starts from the main method and it responsible for taking user
	 * input on which operations are to be performed : 1. Upload a file onto
	 * servers 2. Request and download a file from servers
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		Client client = new Client();
		try {

			boolean exit = false;
			Scanner reader = new Scanner(System.in);
			while (!exit) {
				System.out.println(
						"\nOptions :\n\t1. Upload file onto server.\n\t2. Download file from servers.\n\t3. Exit");
				System.out.print("Enter your option : \t");
				String fileName;

				// switch to performing required operation
				switch (reader.nextInt()) {
				case 1:
					System.out.println("Enter FILE NAME?");
					fileName = reader.next();

					// upload file onto server
					client.sendToServer(fileName);
					break;
				case 2:
					System.out.println("Enter FILE NAME?");
					fileName = reader.next();

					// request file to be downloaded from servers, if available
					downloadData(client, fileName);
					break;
				case 3: // exit client service
					exit = true;
					break;
				default:
					System.out.println("Illegal option input");
				}

			}
			reader.close();
		} catch (Exception e) {
			System.out.println("Exception in Client: " + e);
		}
	} // main

}// Client
