package central_node_pkg;

import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.log4j.*;

import comm_proto.FragmentFile;

/*
 * PublishedFile class represents how the central node stores information
 * about each file published by the clients
 * It has information about the file name, the file size and a list of FragmentFile
 * objects
 * It also allows the central node to add a new client to a specific fragment
 */
public class PublishedFile {
	
	private String fileName;
	private int nrFragments;
	private int fileSize;
	private ArrayList<FragmentFile>  fragments;
	private final static Logger logger = Logger.getLogger(PublishedFile.class);
	
	public PublishedFile(String name, int fileSize, int firstFragmentSize, InetSocketAddress publisher){
		
		this.fileName = name;
		this.fileSize = fileSize;
	
		//given the fragment size of first fragment, it is able to split the
		//file as the client splitted it
		this.nrFragments = this.fileSize / firstFragmentSize;
		if (this.fileSize % firstFragmentSize != 0)
			this.nrFragments++;
		
		this.fragments = new ArrayList<FragmentFile>(this.nrFragments);
		
		for(int i = 0; i < this.nrFragments -1; ++i) {
			this.fragments.add(new FragmentFile(firstFragmentSize, publisher));
		}
	
		if (this.fileSize % firstFragmentSize != 0)
			this.fragments.add(new FragmentFile(this.fileSize % firstFragmentSize, publisher));

		else
			this.fragments.add(new FragmentFile(firstFragmentSize, publisher));
	}
	
	
	public Boolean addNewClient(int fragmentNr, InetSocketAddress client){
		
		if (fragmentNr >= this.fragments.size())
			return false;
		
		this.fragments.get(fragmentNr).addNewClient(client);
		return true;
	}
	
	/*
	 * this method returns the serialized representation
	 * of the list of fragments so it can be send to the
	 * client in order for it to download the file
	 */
	public byte[] getClientsList(){
		
		ByteArrayOutputStream bos  = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		byte[] clientsList = null;
		try{
			oos = new ObjectOutputStream(bos);
			oos.writeObject(this.fragments);
			oos.flush();
			clientsList = bos.toByteArray();
		} catch(IOException e){
			logger.error(
					"Something went wrong when serializing the clinets that have fragments of the file: "
					+ this.fileName, e);
		} finally {
			try{
				if (oos != null)
					oos.close();
			} catch(IOException ex){
				logger.error(
						"Something went wrong in getClientsList method for file " + this.fileName, ex);
			}
		}
		return clientsList;
	}

	
	@Override
	public String toString() {
		return "PublishedFile [fileName=" + fileName + 
				", nrFragments=" + nrFragments + ", fileSize=" + fileSize  +  "]";
	}
	
	
	
}
