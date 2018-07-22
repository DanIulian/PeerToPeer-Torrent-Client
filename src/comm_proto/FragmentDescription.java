package comm_proto;

import java.io.Serializable;

/*
 * This class is serialized and set across the network when
 * a client download a fragment from a file it wishes to download
 * It informs the central node the fact that a new client has the
 * respective fragment. It provides information about the name of the
 * file whose fragment was downloaded together with the fragment number
 * and the ip of the client
 */

public class FragmentDescription implements Serializable {

	private static final long serialVersionUID = -8840273160808665693L;
	public String clientIpAddress;
	public String fileName;
	public int fragmentNumber;
	
	public FragmentDescription(String ipAdd, String fileName, int fragmentNumber) {
		this.fileName = fileName;
		this.fragmentNumber = fragmentNumber;
		this.clientIpAddress = ipAdd;
	}

	@Override
	public String toString() {
		return "FragmentDescription [clientIpAddress=" + clientIpAddress + ", fileName=" + fileName
				+ ", fragmentNumber=" + fragmentNumber + "]";
	}


	
}
