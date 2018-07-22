package client;

import java.io.Serializable;

/*
 * Objects of this class are send across the network when
 * one client request to download a fragment from another client
 * It has information about the fragment number and the file that
 * has that fragment
 */
public class FragmentRequest implements Serializable {

	private static final long serialVersionUID = 7661662132951702401L;
	public int fragmentNumber;
	public String fileName;
	
	public FragmentRequest(int fragmentNumber, String fileName) {
		this.fragmentNumber = fragmentNumber;
		this.fileName = fileName;
	}

	@Override
	public String toString() {
		return "FragmentRequest [fragmentNumber=" + fragmentNumber + ", fileName=" + fileName + "]";
	}

}
