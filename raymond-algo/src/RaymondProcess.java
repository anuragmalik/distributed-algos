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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * RaymondProcess class provides an implementation of the RAYMOND Mutual
 * exclusion algorithm used in Distributed Systems to allow only single process
 * to be present in the critical section.
 * 
 * @author Anurag Malik, am3926@rit.edu
 */
public class RaymondProcess extends UnicastRemoteObject implements RaymondInterface, Runnable {

	private static final long serialVersionUID = 1L;
	volatile int HOLDER;
	volatile int CHANNELS;
	volatile boolean TOKEN;
	volatile boolean inCS;
	volatile boolean reqMade;

	volatile int procId;
	Random random;
	String[] processes;

	ArrayList<Integer> reqQueue;
	HashSet<Integer> reqMap;
	volatile int replyProc, procBuffer;

	Object tokenLock = new Object();
	Object reqLock = new Object();
	Object queueLock = new Object();
	Object execLock = new Object();

	/**
	 * Constructor for creating new Process instance and responsible for
	 * calculating the procId and Holder for the current process.
	 */
	public RaymondProcess(HashMap<String, Integer> servers) throws RemoteException {
		super();
		procId = servers.get(getHostName());
		TOKEN = false;
		random = new Random();

		processes = new String[servers.size()];
		for (String val : servers.keySet())
			processes[servers.get(val)] = val;

		// if this is process zero, it has token
		if (procId != 0)
			changeHolder((procId - 1) / 2);
		else
			TOKEN = true;

		inCS = false;
		CHANNELS = processes.length - 1;

		reqQueue = new ArrayList<Integer>();
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

	/**
	 * Get connection to a process with required input ID.
	 * 
	 * @param procId
	 * @return
	 */
	private RaymondInterface getConnectionToServer(int procId) {
		String serverName = processes[procId] + ".cs.rit.edu";
		String hostName = "rmi://" + serverName + ":" + 4040 + "/server";
		while (true) {
			try {
				return (RaymondInterface) Naming.lookup(hostName);
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
			}
		}
	}

	/**
	 * This method provides the multi-threaded implementation allowing a process
	 * to make request for entering the Critical Section.
	 */
	private void enterCS() {

		// if the token is available, execute CS
		if (TOKEN) {
			if (reqQueue.isEmpty()) {
				executeCS();
				return;
			}
		}

		// else add self to queue and request holder
		reqQueue.add(procId);
		requestHolder();

		try {
			synchronized (execLock) {
				execLock.wait();

				// wait until token is not received
				executeCS();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method represents the exit point for the CS for a process.
	 */
	private void exitCS() {
		System.out.println("*** EXITED CS ***");

		// forward token to the next process in queue
		forwardToken();
		System.out.println();
		inCS = false;
	}

	/**
	 * The method allows the process to forward the token to the next process
	 * waiting in queue. If the next process is self, enter CS
	 */
	private void forwardToken() {
		synchronized (queueLock) {
			if (reqQueue.isEmpty()) {
				System.out.println("Empty Request Queue");
				return;
			}

			// dequeue next process from Queue
			int rem = reqQueue.remove(0);
			if (rem == procId) {
				// self, enter CS
				synchronized (execLock) {
					execLock.notify();
					return;
				}
			}

			// else, pass token to another process and if the request queue for
			// current process is not empty, request the token back from new
			// holder
			displayQueue();
			passToken(rem);
			changeHolder(rem);
			if (!reqQueue.isEmpty()) {
				System.out.println("Request Queue not empty - Requesting Holder");
				requestHolder();
			}
		}
	}

	/**
	 * This method represents the CS for a process.
	 */
	private void executeCS() {
		inCS = true;
		System.out.println("\n*** ENTERED CS ***");
		try {
			Thread.sleep(3000);
			exitCS();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method represents a broadcasting method, a process connects to all
	 * other processes in a distributed network and sends a CS request.
	 */
	private void requestHolder() {
		try {
			if (reqMade)
				return;
			reqMade = true;

			System.out.println("( Requesting Holder - " + processes[HOLDER] + " )");
			getConnectionToServer(HOLDER).receiveReq(procId);

		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This methods allows a process to connect to another process and send
	 * token to it.
	 * 
	 * @param proc
	 */
	public void passToken(int proc) {
		try {
			TOKEN = false;
			System.out.println("@@@ Sending Token : " + processes[proc]);
			getConnectionToServer(proc).receiveToken(procId);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	/**
	 * This method is responsible for listening to any incoming Tokens.
	 */
	public void receiveToken(int proc) {
		synchronized (tokenLock) {
			tokenLock.notify();
		}
	}

	@Override
	/**
	 * This method receives any CS request from another processes.
	 */
	public void receiveReq(int procId) {
		synchronized (reqLock) {
			procBuffer = procId;
			reqLock.notify();
		}
	}

	/**
	 * RequestReader thread is responsible for listening to any new requests and
	 * deciding weather to pass it token or to request holder for getting token.
	 */
	public void requestReader() {
		while (true) {
			try {
				synchronized (reqLock) {
					reqLock.wait();
					System.out.println("NEW CS REQUEST : " + processes[procBuffer]);
					System.out.print("\tAction : ");

					// Add this new request to the request queue
					synchronized (queueLock) {
						if (!reqQueue.contains(procBuffer)) {
							System.out.println("Queued");
							reqQueue.add(procBuffer);
						} else
							System.out.println("Already Queued");
						displayQueue();
					}

					synchronized (tokenLock) {
						if (TOKEN) {
							// If the token is available,
							if (!inCS) {
								// If not in CS, pass token to the requesting
								// process
								int reqProc = reqQueue.remove(0);
								displayQueue();
								TOKEN = false;
								passToken(reqProc);
								changeHolder(reqProc);
							}
						} else
							// else request your holder for token
							requestHolder();
					}
					procBuffer = -1;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void displayQueue() {
		System.out.println("QUEUE ");
		Iterator<Integer> itr = reqQueue.iterator();
		while (itr.hasNext())
			System.out.print(" --> " + processes[itr.next()]);
		System.out.println();
	}

	/**
	 * This method is responsible for changing the holder of its process
	 * 
	 * @param proc
	 */
	public void changeHolder(int proc) {
		HOLDER = proc;
		System.out.println("--> HOLDER changed : " + processes[proc]);
	}

	/**
	 * TokenReader thread is responsible for listening to new token being
	 * received and forward to the requests waiting in queue.
	 */
	public void tokenReader() {
		while (true) {
			try {
				synchronized (tokenLock) {
					tokenLock.wait();
					System.out.println("\n### TOKEN RECEIVED");
					TOKEN = true;
					reqMade = false;
					forwardToken();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		HashMap<String, Integer> servers = new HashMap<String, Integer>();
		switch (Integer.parseInt(args[0])) {
		case 8:
			servers.put("yes", 7);
		case 7:
			servers.put("medusa", 6);
		case 6:
			servers.put("delaware", 5);
		case 5:
			servers.put("buddy", 4);
		case 4:
			servers.put("doors", 3);
		case 3:
			servers.put("newyork", 2);
		case 2:
			servers.put("kansas", 1);
		case 1:
			servers.put("glados", 0);
			break;
		default:
			System.out.println("Invalid input");
			System.exit(-1);
		}

		RaymondProcess process;
		try {
			process = new RaymondProcess(servers);

			RaymondInterface procInt = process;
			Registry registry = LocateRegistry.createRegistry(4040);
			registry.rebind("server", procInt);
			Thread proc = new Thread(process, "procExec");
			Thread req = new Thread(process, "reqHandler");
			Thread token = new Thread(process, "tokenHandler");

			Thread.sleep(2000);

			proc.start();
			req.start();
			token.start();
		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}

	}

	@Override
	/**
	 * Run method overridden implementation for
	 */
	public void run() {
		String type = Thread.currentThread().getName();
		if (type.equals("procExec")) {
			// start main CS requesting thread
			exec();
		} else if (type.equals("reqHandler")) {
			// start request listening thread
			requestReader();
		} else if (type.equals("tokenHandler")) {
			// request token listening thread
			tokenReader();
		}
	}

	/**
	 * Thread for requesting CS at random intervals
	 */
	public void exec() {
		try {
			while (true) {
				Thread.sleep(Math.abs(random.nextInt() % 4000 + 3000));
				enterCS();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
