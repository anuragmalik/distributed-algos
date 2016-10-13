import java.io.Serializable;
import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Trace class represents a trace of request being served on a distributed
 * systems network. Each server node adds its details to the trace for a file
 * search or download request.
 * 
 * @author Anurag Malik, am3926
 *
 */
public class Trace implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean status;
	private ArrayList<String> trace;

	public Trace() {
		status = false;
		trace = new ArrayList<String>();
	}

	/**
	 * Set status of the trace :
	 * True if the file is found
	 * False otherwise
	 * @param status
	 */
	public void setStatus(boolean status) {
		this.status = status;
	}

	/**
	 * Add host name of a server who received a file search request.
	 * @param hostname
	 */
	public void addToTrace(String hostname) {
		trace.add(hostname);
	}

	/**
	 * Return all details of trace in string format.
	 * @return
	 */
	public String getTrace() {
		String trace = "*** RESPONSE ***\nStatus : File not Found";
		if (status == false)
			return trace;

		trace = "*** RESPONSE ***\nStatus : File Found\nTrace :\n\t";
		ListIterator<String> iterator = this.trace.listIterator();
		while (iterator.hasNext())
			trace += iterator.next() + "\n\t";

		return trace;
	}
}
