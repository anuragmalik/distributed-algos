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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;


/**
 * RAProcess class represents a demo of how Ricart - Agarwal mutual exclusion
 * algorithm works when implemented on a set of distributed systems servers.
 * 
 * @author Anurag Malik, am3926@rit.edu
 */
public class RAProcess extends UnicastRemoteObject implements RAInterface, Runnable {

	private static final long serialVersionUID = 1L;
	int procId, eventCount;
	int value;
	VectorClock vectorClock;
	Random random;
	String[] processes;
	String STATUS;
	final int CHANNELS;
	int procBuffer, replyProc;
	VectorClock clockBuffer;
	VectorClock timeStamp;

	Object entryLock = new Object();
	Object reqLock = new Object();
	Object repLock = new Object();
	Object exitLock = new Object();

	ArrayList<Integer> reqQueue;
	ArrayList<Integer> repBuffer;

	/**
	 * Constructor for RAProcess - responsible for creating new object
	 * instances.
	 * 
	 * @param servers
	 * @throws RemoteException
	 */
	public RAProcess(HashMap<String, Integer> servers) throws RemoteException {
		super();
		STATUS = "RELEASED";

		// fetch and set process id of new instance
		procId = servers.get(getHostName());
		random = new Random();

		// set vector clock for this new instance
		vectorClock = new VectorClock(procId, servers.size());
		eventCount = 5;
		value = 1000;
		processes = new String[servers.size()];
		for (String val : servers.keySet())
			processes[servers.get(val)] = val;

		CHANNELS = processes.length - 1;

		// create new empty request queue and reply buffer for this new instance
		reqQueue = new ArrayList<Integer>();
		repBuffer = new ArrayList<Integer>();
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
	 * This method acts as the entry point for a process to request for a shared
	 * critical section among multiple other process. such that only one process
	 * is allowed to enter the critical section at a given point in time.
	 */
	private void enterCS() {
		STATUS = "WANTED";
		System.out.print("*** REQUESTING CS ***");
		System.out.println("\tSTATUS : " + STATUS);
		// update vector clock for a new event
		vectorClock.internalEvent();

		// broadcast request for entering into new Critical Section
		multiCastRequest();
		System.out.println();
		
		try {
			synchronized (entryLock) {
				// wait until n-1 replies are received
				while (repBuffer.size() < CHANNELS) {
					entryLock.wait();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// On receiving n - 1 replies, enter the critical section
		executeCS();
	}

	/**
	 * This method represents entry into critical section. Any process entering
	 * its critical section can send money to other processes.
	 */
	private void executeCS() {
		STATUS = "HELD";
		System.out.print("*** ENTERED CS ***");
		System.out.println("\tSTATUS : " + STATUS);
		try {
			// clear replies buffer
			repBuffer.clear();

			// sleep and then send money to other processes
			Thread.sleep(2000);
			for (int i = 0; i < 2; i++)
				sendMoney();

			System.out.println();

			// after completing all task in CS, exit
			exitCS();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method represents an exit from the critical section. Any process
	 * exiting the critical section should send replies to all the processes
	 * waiting in its queue.
	 */
	private void exitCS() {
		STATUS = "RELEASED";
		System.out.print("*** EXITED CS ***");
		System.out.println("\tSTATUS : " + STATUS);
		timeStamp = null;
		synchronized (exitLock) {

			// send replies to all the processes waiting in queue
			Iterator<Integer> itr = reqQueue.iterator();
			while (itr.hasNext())
				replyCS(itr.next());

			// clear replies buffer
			reqQueue.clear();
		}
		System.out.println();
	}

	/**
	 * This method represents a broadcasting method, a process connects to all
	 * other processes in a distributed network and sends a CS request.
	 */
	private void multiCastRequest() {

		// save a copy of the current vector time stamp
		timeStamp = vectorClock.getCopy();
		try {

			// send CS requests to all other processes.
			for (int i = 0; i < processes.length; i++) {
				if (i != procId) {
					System.out.println("Requesting CS - " + processes[i]);
					getConnectionToServer(i).requestCS(procId, timeStamp.getCopy(), vectorClock.getCopy());
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get connection to a process with required input ID.
	 * 
	 * @param procId
	 * @return
	 */
	private RAInterface getConnectionToServer(int procId) {
		String serverName = processes[procId] + ".cs.rit.edu";
		String hostName = "rmi://" + serverName + ":" + 4040 + "/server";
		while (true) {
			try {
				return (RAInterface) Naming.lookup(hostName);
			} catch (MalformedURLException | RemoteException | NotBoundException e) {
				System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
			}
		}
	}

	/**
	 * This method is responsible for sending random amount of money to other
	 * processes.
	 */
	private void sendMoney() {
		int tran = Math.abs(random.nextInt(90)) + 10;
		int proc = procId;
		while (proc == procId)
			proc = Math.abs(random.nextInt()) % (CHANNELS + 1);

		// update value at the sending process
		updateAmount(-tran, null);

		try {
			// get connection to another process and send money
			System.out.println("$$$ Sending " + tran + " to - " + processes[proc]);
			getConnectionToServer(proc).acceptDeposit(tran, vectorClock.getCopy(), procId);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method updates any change in the value of current process and also
	 * makes appropriate changes to the vector clock
	 * 
	 * @param amt
	 * @param timeStamp
	 */
	private void updateAmount(int amt, VectorClock timeStamp) {
		if (timeStamp == null)
			vectorClock.internalEvent();
		else
			vectorClock.externalEvent(timeStamp);

		value += amt;
	}

	@Override
	/**
	 * This method receives any money sent by another processes.
	 */
	public void acceptDeposit(int amount, VectorClock clock, int procId) throws RemoteException {
		System.out.println("$$$ Received " + amount + " from " + processes[procId]);
		updateAmount(amount, clock);
	}

	@Override
	/**
	 * This method receives any CS request from another processes.
	 */
	public void requestCS(int procId, VectorClock timeStamp, VectorClock newTime) {
		synchronized (reqLock) {
			procBuffer = procId;
			clockBuffer = timeStamp;

			// log an external event
			vectorClock.externalEvent(newTime);
			reqLock.notify();
		}
	}

	/**
	 * This is a threaded method, a thread always keeps looking for any new CS
	 * requests and decides whether to reply or not.
	 */
	private void requestReader() {
		while (true) {
			try {
				synchronized (reqLock) {
					// wait for any new request
					reqLock.wait();

					System.out.println("*** NEW CS REQUEST : " + processes[procBuffer]);
					System.out.println("\tStatus : " + STATUS);
					if (timeStamp != null)
						System.out.println("\tSaved : " + timeStamp.getTime());
					System.out.println("\tReceived : " + clockBuffer.getTime());
					System.out.print("\tAction : ");

					// decide whether to reply or to enqueue this new request
					if (!replyToRequest()) {
						synchronized (exitLock) {

							// add to queue
							System.out.println("Queued");
							reqQueue.add(procBuffer);
						}
					} else {

						// send reply to another process, allowing access to CS
						System.out.println("Replied");
						replyCS(procBuffer);
					}
					procBuffer = -1;
					clockBuffer = null;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println();
		}
	}

	/**
	 * This method is responsible for making the decision of whether to reply
	 * affirmative to a new CS request or to not reply it and enqueue.
	 * 
	 * @return boolean - reply or not
	 */
	private boolean replyToRequest() {

		if (STATUS.equals("RELEASED")) {
			return true;
		}
		if (STATUS.equals("HELD")) {
			return false;
		}

		// compare vector time stamps
		int timeCompare = timeStamp.compare(clockBuffer);
		if (STATUS.equals("WANTED") && timeCompare < 0) {
			return false;
		}

		// if vector time stamps are same, compare process IDs
		if (STATUS.equals("WANTED") && timeCompare == 0) {
			return procId < procBuffer ? false : true;
		}
		return true;

	}

	/**
	 * This method is responsible for keep checking if there is a new reply from
	 * another process, allowing the current process to enter the critical
	 * section. This method is threaded- each time a new reply is received, the
	 * thread adds it to the reply buffer.
	 */
	private void replyReader() {
		while (true) {
			try {
				synchronized (repLock) {
					repLock.wait();

					if (STATUS.equals("WANTED")) {
						synchronized (entryLock) {

							// add the current process to replies buffer
							repBuffer.add(replyProc);

							// check if the current process can enter CS
							entryLock.notify();
						}
					} else {
						System.out.println("***ERROR***");
					}

					replyProc = -1;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	/**
	 * Receive reply from another process.
	 */
	public void recieveReply(int procID) {
		synchronized (repLock) {
			replyProc = procId;
			repLock.notify();
		}
	}

	@Override
	/**
	 * Reply to another process, for allowing it to enter CS
	 */
	public void replyCS(int proc) {
		try {
			getConnectionToServer(proc).recieveReply(procId);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		HashMap<String, Integer> servers = new HashMap<String, Integer>();
		servers.put("glados", 0);
		servers.put("kansas", 1);
		servers.put("newyork", 2);
		servers.put("doors", 3);

		RAProcess process;
		try {
			process = new RAProcess(servers);

			RAInterface procInt = process;
			Registry registry = LocateRegistry.createRegistry(4040);
			registry.rebind("server", procInt);
			System.out.println("Process @ : " + process.getHostName());

			// create new threads for handling different tasks at a system.
			Thread proc = new Thread(process, "procExec");
			Thread req = new Thread(process, "reqHandler");
			Thread rep = new Thread(process, "repHandler");

			Thread.sleep(2000);

			proc.start();
			req.start();
			rep.start();
		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
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
		} else if (type.equals("repHandler")) {
			replyReader();
		}
	}

	/**
	 * This method is responsible for handling the thread that keeps trying to
	 * enter CS after a random gap of some seconds.
	 */
	public void exec() {
		try {
			while (true) {
				Thread.sleep(Math.abs(random.nextInt() % 4000 + 1000));
				enterCS();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

/**
 * Vector Clock class represents an instance of the vector clock used for time
 * stamping purpose between different processes in a distributed systems
 * network.
 * 
 * @author Anurag Malik, am3926@rit.edu
 */
class VectorClock implements Serializable {
	private static final long serialVersionUID = 1L;
	int[] clock;
	int id;

	// acts like a copy constructor, create new instance from another instance
	VectorClock(int nid, int[] nclock) {
		id = nid;
		clock = Arrays.copyOf(nclock, nclock.length);
	}

	VectorClock(int selfId, int s) {
		this.id = selfId;
		this.clock = new int[s];
	}

	// increase vector clock for the current process, increase corresponding bit
	// by one.
	public void internalEvent() {
		System.out.print("*** Clock : " + getTime() + " --> ");
		clock[id] += 1;
		System.out.println(getTime() + " ***");
	}

	// return vector clock for the current process
	public int[] getTimeStamp() {
		return clock;
	}

	// get a new copy/ vector clock of the current clock
	public VectorClock getCopy() {
		return new VectorClock(id, clock);
	}

	/*
	 * This method depicts an external event and updates the vector clock from
	 * such external events.
	 * 
	 */
	public void externalEvent(VectorClock pclock) {
		int i = 0;
		int[] vclock = pclock.getTimeStamp();
		System.out.print("*** Clock : " + getTime() + " --> ");
		clock[id] += 1;

		// compare vector clock bit by bit
		for (int val : clock) {
			if (vclock[i] > val)
				clock[i] = vclock[i];
			i++;
		}

		System.out.println(getTime() + " ***");
	}

	/*
	 * This method returns the time stamp of vector clock
	 */
	public String getTime() {
		String timeStamp = "";
		for (int val : clock)
			timeStamp += val + " : ";
		return timeStamp.substring(0, timeStamp.length() - 2);
	}

	/*
	 * This method compares two vector clocks.
	 */
	public int compare(VectorClock pClock) {
		int[] tClock = pClock.getTimeStamp();
		int index = 0, sCount = 0, lCount = 0;
		for (int val : clock) {
			// count number of smaller bits
			if (val < tClock[index])
				sCount++;

			// count number of larger bits
			if (val > tClock[index])
				lCount++;
			index++;
		}

		if (sCount != 0) {
			if (lCount != 0) {

				// some large, some small bits, no relationship
				System.out.print(getTime() + " ? " + pClock.getTime() + " : ");
				return 0;
			}

			// current vector clock is smaller than the other vector clock
			System.out.print(getTime() + " < " + pClock.getTime() + " : ");
			return -1;
		}

		if (lCount != 0) {
			if (sCount != 0) {
				// some large, some small bits, no relationship
				System.out.print(getTime() + " ? " + pClock.getTime() + " : ");
				return 0;
			}
			// current vector clock is larger than the other vector clock
			System.out.print(getTime() + " > " + pClock.getTime() + " : ");
			return 1;
		}

		// both the vector clocks are same.
		System.out.print(getTime() + " = " + pClock.getTime() + " : ");
		return 0;
	}
}
