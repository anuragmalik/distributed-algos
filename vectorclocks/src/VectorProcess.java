import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Random;

class VectorClock implements Serializable {
	private static final long serialVersionUID = 1L;
	int[] clock;
	int id;

	public void internalEvent() {
		clock[id] += 1;
	}

	public int[] getTimeStamp() {
		return clock;
	}

	public void externalEvent(VectorClock pclock) {
		int i = 0;
		int[] vclock = pclock.getTimeStamp();

		clock[id] += 1;
		for (int val : clock) {
			if (vclock[i] > val)
				clock[i] = vclock[i];
			i++;
		}
	}

	public String getTime() {
		String timeStamp = "";
		for (int val : clock)
			timeStamp += val + " : ";
		return timeStamp.substring(0, timeStamp.length() - 2);
	}
}

public class VectorProcess extends UnicastRemoteObject implements ProcessInterface, Runnable {

	private static final long serialVersionUID = 1L;
	int procId, eventCount;
	int value;
	VectorClock vectorClock;
	Random random;
	String[] processes;

	public VectorProcess(HashMap<String, Integer> servers) throws RemoteException {
		super();
		procId = servers.get(getHostName());
		random = new Random();
		vectorClock = new VectorClock(procId);
		eventCount = 5;
		value = 1000;
		processes = new String[servers.size()];
		for (String val : servers.keySet())
			processes[servers.get(val)] = val;
	}

	public int getProcId() {
		return procId;
	}

	public void run() {
		int action;
		try {
			while (eventCount-- > 0) {
				Thread.sleep(5000);
				action = Math.abs(random.nextInt()) % 3;
				processEvent(action);
			}

			System.out.println("\n*** @" + getHostName() + ", process " + procId + ", Total Amount = " + value + " ***");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void acceptDeposit(int amount, VectorClock clock, int procId) {
		System.out.println("Received deposit from " + processes[procId]);
		updateAmount(amount, clock);
	}

	private void deposit() {
		int tran = Math.abs(random.nextInt(90)) + 10;
		updateAmount(tran, null);
	}

	private void sendMoney() {
		int tran = Math.abs(random.nextInt(90)) + 10;
		int proc = procId;
		while (proc == procId)
			proc = Math.abs(random.nextInt()) % 3;
		updateAmount(-tran, null);

		try {
			getConnectionToServer(proc).acceptDeposit(tran, vectorClock, procId);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private void withdrawl() {
		int tran = Math.abs(random.nextInt(90)) + 10;
		updateAmount(-tran, null);
	}

	private void updateAmount(int amt, VectorClock timeStamp) {
		if (timeStamp == null)
			vectorClock.internalEvent();
		else
			vectorClock.externalEvent(timeStamp);

		logEvent(amt, vectorClock.getTime());
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
		String hostName = "rmi://" + serverName + ":" + 4040 + "/server";
		while (true) {
			try {
				return (ProcessInterface) Naming.lookup(hostName);
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
			}
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

		}
		return null;
	}

	private void logEvent(int amount, String timeStamp) {
		String log = "Process " + procId + " : ";
		if (amount > 0)
			log += String.format("%15s", "Deposit : $") + amount;
		else
			log += String.format("%15s", "Withdrawl : -$") + -amount;
		log += " - " + String.format("%12s", timeStamp);

		System.out.println(log);
	}

	private void processEvent(int action) {
		switch (action) {
		case 0:
			withdrawl();
			break;
		case 1:
			deposit();
			break;
		case 2:
			sendMoney();
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

		VectorProcess process;
		try {
			process = new VectorProcess(servers);

			ProcessInterface procInt = process;
			Registry registry = LocateRegistry.createRegistry(4040);
			registry.rebind("server", procInt);

			// if the server is itself entry point for chord, set its inChord
			// flag true.
			System.out.println("Process @ : " + process.getHostName());
			Thread t1 = new Thread(process);
			t1.start();

		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}
	}
}
