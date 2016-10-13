import java.util.HashMap;

class SnapShot {
	int value, id;
	HashMap<String, Integer> channels;

	SnapShot(int id, int val, HashMap<Integer, String> inChannels) {
		this.id = id;
		value = val;
		channels = new HashMap<String, Integer>();
		for (int cid : inChannels.keySet())
			if (cid != id)
				channels.put(inChannels.get(cid), 0);
	}

	public void updateChannelValue(String channel, int value) {
		value += channels.get(channel);
		channels.put(channel, value);
	}

	public void updateValue(int val) {
		value = val;
	}

	public void clearSnap() {
		value = 0;
		for (String key : channels.keySet()) {
			channels.put(key, 0);
		}
	}

	public void printSnapShot() {
		System.out.println("**SNAP SHOT**");
		System.out.println("\tValue :" + value);
		for (String channel : channels.keySet()) {
			System.out.println("\tChannel to " + channel + " has value - " + channels.get(channel));
		}
	}
}
