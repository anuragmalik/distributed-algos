import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


public class SnapProcess extends UnicastRemoteObject implements ProcessInterface, Runnable {

	private static final long serialVersionUID = 1L;
	int procId;
	volatile int value;
	SnapShot snap;
	Random random;
	String[] processes;
	volatile boolean markerReceived;
	HashMap<Integer, Integer> channelBuffer;
	ArrayList<Integer> inChannels;
	static int SIZE = 2;
	boolean startMode = false;
	static int PORT = 4141;

	public SnapProcess(HashMap<String, Integer> servers) throws RemoteException {
		super();
		procId = servers.get(getHostName());
		random = new Random();
		value = 1000;
		SIZE = servers.size();
		markerReceived = false;
		processes = new String[servers.size()];
		inChannels = new ArrayList<Integer>();

		channelBuffer = new HashMap<Integer, Integer>();
		for (String val : servers.keySet())
			processes[servers.get(val)] = val;
		snap = new SnapShot(procId, value, processes);
	}

	public int getProcId() {
		return procId;
	}

	public void run() {
		int event = 0;
		int count = 3;
		try {
			Thread.sleep(5000);
			while (--count > 0) {
				Thread.sleep(5000);
				processEvent(1);

				event += 1;
				event %= 2;
				if (procId == 0 && event == 0)
					processEvent(2);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void acceptDeposit(int amount, int procId) {
		System.out.println("Received deposit from " + processes[procId]);
		if (!markerReceived)
			updateAmount(amount);
		else {
			if (channelBuffer.containsKey(procId))
				channelBuffer.put(procId, channelBuffer.get(procId) + amount);
			else
				channelBuffer.put(procId, amount);
		}
	}

	private void sendMoney() {
		int tran = Math.abs(random.nextInt(190)) + 10;
		int proc = procId;
		while (proc == procId)
			proc = Math.abs(random.nextInt()) % SIZE;
		updateAmount(-tran);

		try {
			getConnectionToServer(proc).acceptDeposit(tran, procId);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private synchronized void updateAmount(int amt) {
		System.out.println("Transaction of amount :" + amt);
		value += amt;
	}

	/**
	 * This method is used by a server in the distributed network to get
	 * connection to another server in the network, through RMI lookup.
	 * 
	 * @param serverName
	 * @return
	 */
	private ProcessInterface getConnectionToServer(int procId) {
		String serverName = processes[procId] + ".cs.rit.edu";
		System.out.println("Sending money to process at : " + serverName);
		String hostName = "rmi://" + serverName + ":" + PORT + "/server";
		try {
			return (ProcessInterface) Naming.lookup(hostName);
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
		}
		return null;
	}

	/**
	 * Return host name for this server
	 * 
	 * @return host name of the current server.
	 */
	public String getHostName() {
		// return "glados";
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {

		}
		return null;
	}

	private void initiateSnapShot() {
		System.out.println("***Snapshot Initiated");
		markerReceived = true;
		saveSnapShot();
		sendMarkerToChannels();
	}

	private void sendMarkerToChannels() {
		int index = 0;
		while (index < processes.length) {
			System.out.println("LOOPING ** " + index + ": " + (processes.length));
			if (procId != index) {
				try {
					if (!inChannels.contains(index)) {
						System.out.println("**forward markers");
						getConnectionToServer(index).sendMarker(procId);
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			index++;
		}
	}

	private void saveSnapShot() {
		snap.clearSnap();
		snap.updateValue(value);
	}

	private void finishSnapShot() {
		snap.printSnapShot();
		snap.clearSnap();
		markerReceived = false;
		startMode = false;
		inChannels = new ArrayList<Integer>();
		System.out.println("Snapshot complete.");
	}

	@Override
	public void sendMarker(int id) {
		
		System.out.println("***Received marker from " + processes[id]);
		if (!markerReceived) {
			System.out.println("***first time ");
			markerReceived = true;
			saveSnapShot();
			snap.updateChannelValue(processes[id], 0);
			startMode = true;
		} else {
			saveIncomingPortState(id);
		}
		if (markerReceived)
			sendMarkerToChannels();
		
		if (startMode) {
			inChannels.add(id);
			startMode = false;
		}
		
		if (inChannels.size() == (SIZE - 1))
			finishSnapShot();

	}

	private void saveIncomingPortState(int id) {
		int val = 0;
		if (channelBuffer.containsKey(id))
			val = channelBuffer.get(id);
		snap.updateChannelValue(processes[id], val);
		channelBuffer.put(id, 0);

		System.out.println("Adding " + id + " to inChannels @" + getHostName());
		inChannels.add(id);
	}

	private void processEvent(int action) {
		switch (action) {
		case 1:
			sendMoney();
			break;
		case 2:
			initiateSnapShot();
			break;
		default:
			System.out.println("Invalid action!!");
		}
	}

	public static void main(String[] args) {
		HashMap<String, Integer> servers = new HashMap<String, Integer>();
		servers.put("glados", 0);
		servers.put("kansas", 1);
		servers.put("newyork", 2);
		
		PORT = Integer.parseInt(args[0]); 

		SnapProcess process;
		try {
			process = new SnapProcess(servers);

			ProcessInterface procInt = process;
			Registry registry = LocateRegistry.createRegistry(PORT);
			registry.rebind("server", procInt);

			// if the server is itself entry point for chord, set its inChord
			// flag true.
			System.out.println("Process @ : " + process.getHostName());
			Thread teller = new Thread(process, "teller");
			Thread snaper = new Thread(process, "saver");
			Thread listener = new Thread(process, "listener");
			
			teller.run();
			snaper.run();
			listener.run();

		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}
	}
}
