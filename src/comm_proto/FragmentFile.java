package comm_proto;

import java.util.*;
import java.io.Serializable;
import java.net.InetSocketAddress;

/*
 * this class abstract the information about a fragment of a file
 * It contains information about the fragment size along with a list
 * of InetSocketAddress object that represents all the clients that 
 * has this specific fragment
 */
public class FragmentFile implements Serializable {

	private static final long serialVersionUID = 1L;
	private int fragmentSize;
	private ArrayList<InetSocketAddress> clients;
	
	public FragmentFile(int size, InetSocketAddress cl){
		this.fragmentSize = size;
		this.clients = new ArrayList<InetSocketAddress>();
		this.clients.add(cl);
	}
	
	public synchronized void  addNewClient(InetSocketAddress cl){
		this.clients.add(cl);
	}
	
	public int getFragmentSize(){
		return this.fragmentSize;
	}
	
	public ArrayList<InetSocketAddress> getClients(){
		return this.clients;
	}
	
	@Override
	public String toString() {
		return "FragmentFile [fragmentSize=" + fragmentSize + ", clients=" + clients + "]";
	}


}
