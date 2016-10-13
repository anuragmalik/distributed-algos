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
 * Distributed System : This class represents a process capable of sending and
 * receiving money from other processes on distributed networks. A global
 * snapshot is initiated every two seconds from one of the processes, which
 * records the amount left at each process and its incoming channels at a given
 * point in time.
 * 
 * @author Anurag Malik, am3926@rit.edu
 */
public class Process extends UnicastRemoteObject implements ProcessInterface, Runnable {
	private static final long serialVersionUID = 1L;
	static HashMap<Integer, String> servers = new HashMap<Integer, String>();
	static int PORT = 4042;
	int procId, SIZE;

	volatile int depositBuffer = -1;
	volatile int vprocBuffer = -1;
	volatile int procBuffer = -1;
	volatile static boolean snapshotActive;
	volatile static boolean firstMarker;

	static SnapShot snap;

	volatile static int value;
	static Object amountLock = new Object();
	static Object sendLock = new Object();
	static Object recLock = new Object();
	static Object markerLock = new Object();
	static Object depositLock = new Object();
	static Object snapLock = new Object();

	ArrayList<Integer> markerList = null;

	protected Process() throws RemoteException {
		super();
		String serverName = getHostName();

		for (int id : servers.keySet()) {
			if (servers.get(id).equals(serverName)) {
				procId = id;
				break;
			}
		}

		SIZE = servers.size();
		value = 1000;
		snapshotActive = false;

		// create a new snapshot class instance
		snap = new SnapShot(procId, value, servers);
	}

	@Override
	public void run() {
		int count = 6;
		int action = 0;
		String type = Thread.currentThread().getName();
		if (type.equals("valHandler")) {
			action = 1;
		} else if (type.equals("snapTaker") && procId == 0) {
			action = 2;
		} else if (type.equals("valListener")) {
			action = 3;
		} else if (type.equals("markerListener")) {
			action = 4;
		}
		try {
			while (action != 0 && count > 0) {
				switch (action) {
				case 1:

					// thread to handle sending money at a time gap of 1
					// seconds.
					Thread.sleep(1000);
					sendMoney();
					break;
				case 2:

					// thread to initiate global snapshot at a time gap of 2
					// seconds.
					synchronized (snapLock) {
						while (snapshotActive)
							snapLock.wait();
						Thread.sleep(2000);
						initiateSnapShot();
					}
					break;
				case 3:
					getDeposit();
					break;
				case 4:
					markerList = new ArrayList<Integer>();
					getMarker();
					break;
				}
				count--;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void getMarker() {
		while (true) {
			try {
				synchronized (markerLock) {
					markerLock.wait();
				}
				snapShotAtMarker();
				synchronized (depositLock) {
					procBuffer = -1;
					depositLock.notify();
				}

				if (markerList.size() == SIZE - 1) {
					finishSnapShot();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void getDeposit() {
		while (true) {
			try {
				synchronized (depositLock) {
					while (vprocBuffer == -1 || depositBuffer == -1)
						depositLock.wait();

					System.out.println("Deposit of : " + depositBuffer + " from " + servers.get(vprocBuffer));
					updateAmount(depositBuffer);

					if (procBuffer != -1)
						depositLock.wait();
					if (snapshotActive && !firstMarker) {
						System.out.println("Channel deposit : " + depositBuffer + " from " + servers.get(vprocBuffer));
						snap.updateChannelValue(servers.get(vprocBuffer), depositBuffer);
					}

					vprocBuffer = -1;
					depositBuffer = -1;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void snapShotAtMarker() {
		if (snapshotActive) {
			firstMarker = false;
			return;
		}
		synchronized (sendLock) {
			System.out.println("***Snapshot Initiated***");
			snapshotActive = true;
			if (procBuffer != -1)
				snap.updateChannelValue(servers.get(procBuffer), 0);
			firstMarker = true;
			saveSnapShot();
			sendMarkerToChannels();
		}
	}

	private void initiateSnapShot() {
		synchronized (sendLock) {
			System.out.println("***Snapshot Initiated***");
			snapshotActive = true;
			saveSnapShot();
			sendMarkerToChannels();
		}
	}

	private void finishSnapShot() {
		snap.printSnapShot();
		snap.clearSnap();
		markerList.clear();
		snapshotActive = false;
		System.out.println("***Snapshot complete***");
		if (procId == 0) {
			synchronized (snapLock) {
				snapLock.notify();
			}
		}
	}

	private void saveSnapShot() {
		snap.clearSnap();
		snap.updateValue(value);
	}

	private void sendMarkerToChannels() {
		int index = 0;
		try {
			Thread.sleep(1000);
			while (index < servers.size()) {
				if (procId != index) {
					synchronized (sendLock) {
						System.out.println("Sending marker to " + servers.get(index));
						getConnectionToServer(index).sendMarker(procId);
					}
				}
				index++;
			}
		} catch (RemoteException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void sendMarker(int proc) throws RemoteException {
		synchronized (recLock) {
			synchronized (markerLock) {
				procBuffer = proc;

				if (!markerList.contains(procBuffer)) {
					System.out.println("Marker from " + servers.get(procBuffer));
					markerList.add(procBuffer);
				} else {
					System.out.println("***Strange*** again a marker from - " + servers.get(procBuffer));
				}

				markerLock.notify();
			}
		}
	}

	@Override
	public void acceptDeposit(int tran, int proc) throws RemoteException {
		synchronized (recLock) {
			synchronized (depositLock) {
				depositBuffer = tran;
				vprocBuffer = proc;
				depositLock.notify();
			}
		}
	}

	@Override
	public String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
		}
		return null;
	}

	private void updateAmount(int amt) {
		synchronized (amountLock) {
			value += amt;
		}
	}

	private void sendMoney() {
		Random random = new Random();
		int tran = Math.abs(random.nextInt(10)) + 10;
		int proc = procId;
		while (proc == procId)
			proc = Math.abs(random.nextInt()) % SIZE;
		updateAmount(-tran);
		try {
			synchronized (sendLock) {
				System.out.println("Sending to " + servers.get(proc) + " : " + tran);
				getConnectionToServer(proc).acceptDeposit(tran, procId);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private ProcessInterface getConnectionToServer(int procId) {
		String serverName = servers.get(procId) + ".cs.rit.edu";
		String hostName = "rmi://" + serverName + ":" + PORT + "/server";
		try {
			return (ProcessInterface) Naming.lookup(hostName);
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			System.out.println("@" + getHostName() + ": Failure connecting to -" + serverName);
		}
		return null;
	}

	public static void main(String[] args) {
		servers.put(0, "glados");
		servers.put(1, "kansas");
		servers.put(2, "newyork");
		// PORT = Integer.parseInt(args[0]);

		try {
			Process process = new Process();
			ProcessInterface procInt = process;

			Registry registry = LocateRegistry.createRegistry(PORT);
			registry.rebind("server", procInt);

			System.out.println("Process @ : " + process.getHostName());
			Thread valOne = new Thread(process, "valHandler");
			Thread valTwo = new Thread(process, "valListener");
			Thread markOne = new Thread(process, "markerListener");
			Thread markTwo = new Thread(process, "snapTaker");

			Thread.sleep(5000);

			valOne.start();
			valTwo.start();
			markOne.start();
			markTwo.start();

		} catch (Exception exp) {
			System.out.println("Exception @ Server: " + exp);
		}
	}

}
