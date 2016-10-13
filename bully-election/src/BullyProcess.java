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

/**
 * BullyProcess represents a Process involved in Leader Election algorithm using
 * Bully's Algorithm.
 * 
 * @author Anurag Malik, am3926@rit.edu
 *
 */
public class BullyProcess extends UnicastRemoteObject implements BullyInterface, Runnable {

	private static final long serialVersionUID = 1L;
	int procId, eventCount;
	int value;
	Random random;
	String[] processes;
	String STATUS;
	final int CHANNELS;
	int procBuffer, replyProc;

	Object reqLock = new Object();
	Object repLock = new Object();
	Object leaderLock = new Object();

	ArrayList<Integer> reqQueue;
	Integer repBuffer;
	volatile private boolean waiting;
	private Integer leaderBuffer;
	private Integer LEADER;

	/**
	 * BullyProcess constructor for creating a new instance of the process.
	 * 
	 * @param servers
	 *            : list of all servers involved in process
	 * @throws RemoteException
	 */
	public BullyProcess(HashMap<String, Integer> servers) throws RemoteException {
		super();
		// fetch and set process id of new instance
		procId = servers.get(getHostName());
		random = new Random();

		waiting = false;

		value = 1000;
		processes = new String[servers.size()];
		for (String val : servers.keySet())
			processes[servers.get(val)] = val;

		// count the number of other process involved in leader election.
		CHANNELS = processes.length - 1;
	}

	public String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
		}
		return null;
	}

	@Override
	/**
	 * This method provides the interface method for listening to new election
	 * requests.
	 */
	public void election(int proc) throws RemoteException {
		synchronized (reqLock) {
			procBuffer = proc;
			reqLock.notify();
		}

	}

	@Override
	/**
	 * This method provides the interface method for listening to new leader
	 * message.
	 */
	public void leader(int proc) throws RemoteException {
		synchronized (leaderLock) {
			leaderBuffer = proc;
			System.out.println("--> LEADER : " + leaderBuffer);
			LEADER = leaderBuffer;
			leaderLock.notify();
		}
	}

	@Override
	/**
	 * This method provides the interface for listening to replies from other
	 * processes.
	 */
	public void reply(int proc) throws RemoteException {
		synchronized (repLock) {
			repBuffer = proc;
			repLock.notify();
		}

	}

	@Override
	/**
	 * Run method for multi-threaded processes, assigning tasks to threads.
	 */
	public void run() {
		String type = Thread.currentThread().getName();
		if (type.equals("procExec")) {
			exec();
		} else if (type.equals("reqHandler")) {
			requestReader();
		}
	}

	/**
	 * RequestReader method is responsible for listening to new election
	 * requests from other processes. It decides weather to start new election
	 * or not.
	 */
	private void requestReader() {
		synchronized (reqLock) {
			try {
				while (true) {
					reqLock.wait();

					// reply to process that requested election
					System.out.println("Replying to " + procBuffer);
					getConnectionToServer(procBuffer).reply(procId);
					procBuffer = -1;
					if (!waiting)
						// if a election is not already in execution, start new
						// election
						startElection();
				}
			} catch (InterruptedException | RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * This method is responsible for handling the thread that keeps trying to
	 * start election after a random gap of some seconds.
	 */
	public void exec() {
		try {
			while (true) {
				// check if there is no current leader, start new election
				if (LEADER == null) {
					Thread.sleep(Math.abs(random.nextInt() % 2000 + 5000));
					if (!waiting)
						startElection();
				} else {
					// if there is already a leader, keep sending heartbeat msgs
					// to it. & if it goes down, start new election
					Thread.sleep(Math.abs(random.nextInt() % 2000 + 1000));
					if (getConnectionToServer(LEADER) == null) {
						LEADER = null;
						startElection();
					}
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The method contains the functionality of starting a new election for
	 * leader selection. If the process is itself the one with the largest id,
	 * then it sends coordination messages to all other processes.
	 */
	private void startElection() {
		System.out.println("*** ELECTION STARTED ***");
		try {
			boolean restart = true;
			while (restart) {

				// if self is leader
				if (procId == CHANNELS) {
					sendCoordination();
					restart = false;
					return;
				}

				boolean selfLeader = true;
				// try connection to all processes with larger IDs and start an
				// election
				for (int i = procId + 1; i <= CHANNELS; i++) {
					BullyInterface server = getConnectionToServer(i);
					if (server != null) {
						server.election(procId);
						selfLeader = false;
					}
				}

				// if no leader is alive, select self as leader
				if (selfLeader) {
					sendCoordination();
					return;
				}

				waiting = true;
				restart = false;

				// if a reply is not received, select self as leader
				synchronized (repLock) {
					repLock.wait(5000);
					if (repBuffer == null) {
						sendCoordination();
					} else {
						// wait for co-ordination messages
						restart = waitForLeader();
					}
				}
			}
		} catch (RemoteException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This process allows a thread to wait for receiving a new coordination
	 * message / new leader. It none is received, start new election process.
	 * 
	 * @return
	 */
	private boolean waitForLeader() {
		System.out.println("1.) Waiting for Co-ordination Msg");
		try {
			synchronized (leaderLock) {
				// wait for some time
				leaderLock.wait(5000);
				if (leaderBuffer == null) {
					// restart election if no coordination message is received
					System.out.println("2.) Restarting Election");
					return true;
				} else {
					// finish the election.
					System.out.println("2.) Co-ordination Msg recieved");
					System.out.println("*** ELECTION FINISHED *** ( Leader : " + LEADER + " )");
					waiting = false;
					return false;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * this method allows one process to send coordination messages to all other
	 * processes with lower IDs
	 */
	private void sendCoordination() {
		System.out.println("Sending Co-Ordination Msgs");
		LEADER = procId;
		System.out.println("--> LEADER : SELF");
		for (int i = procId - 1; i >= 0; i--) {
			try {
				// connect to process and send leader message to it
				getConnectionToServer(i).leader(procId);
			} catch (RemoteException | NullPointerException e) {
			}
		}

	}

	/**
	 * Process execution starts from this method. It is responsible for creating
	 * new instance of BullyProcess and register it with RMI
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		// dictionary of all processes involved in the leader election process.
		HashMap<String, Integer> servers = new HashMap<String, Integer>();
		servers.put("glados", 0);
		servers.put("kansas", 1);
		servers.put("newyork", 2);
		servers.put("yes", 3);
		servers.put("buddy", 4);

		BullyProcess process;
		try {
			process = new BullyProcess(servers);

			// register the process with RMI Registry
			BullyInterface procInt = process;
			Registry registry = LocateRegistry.createRegistry(4040);
			registry.rebind("server", procInt);
			System.out.println("Process @ : " + process.getHostName());

			// create new internal threads for handling requests from other
			// processes and internal processing.
			Thread proc = new Thread(process, "procExec");
			Thread req = new Thread(process, "reqHandler");
			Thread.sleep(2000);
			proc.start();
			req.start();
		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}
	}

	/**
	 * Get connection to a process with required input ID.
	 * 
	 * @param procId
	 * @return
	 */
	private BullyInterface getConnectionToServer(int procId) {
		String serverName = processes[procId] + ".cs.rit.edu";
		String hostName = "rmi://" + serverName + ":" + 4040 + "/server";
		try {
			return (BullyInterface) Naming.lookup(hostName);
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
		}
		return null;
	}
}
