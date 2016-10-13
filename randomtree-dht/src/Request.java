import java.io.Serializable;

/**
 * This class represents a Request packet. It includes details of file download
 * or search request.
 * 
 * @author Anurag Malik, am3926
 *
 */
public class Request implements Serializable {
	private static final long serialVersionUID = 1L;
	private ClientInterface client;
	private String fileName;
	private int[] server;

	public Request() {
		this.fileName = null;
		this.server = new int[2];
	}

	/**
	 * This method is responsible for packing destination server data
	 * 
	 * @param file
	 * @param server
	 */
	public void packData(String file, int[] server) {
		this.fileName = file;
		int i = 0;
		for (int x : server)
			this.server[i++] = x;
	}

	/**
	 * Return file name of this request packet
	 * 
	 * @return
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Return details of the destination of this request packet
	 * 
	 * @return
	 */
	public int[] getDestination() {
		return server;
	}

	/**
	 * Return details of the client who requested file with this request packet.
	 * 
	 * @return
	 */
	public ClientInterface getClient() {
		return client;
	}

	public void setDestination(int[] destinationServer) {
		int i = 0;
		for (int x : destinationServer)
			server[i++] = x;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setClient(ClientInterface callBack) {
		this.client = callBack;
	}

}
