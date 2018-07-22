package comm_proto;

import java.io.Serializable;

/*
 * This class is serialized and sent across the network 
 * when a client first publish a file to the central node
 * It contains information about the file name, the file size,
 * the size of the first fragment of the file, and lastly the ip 
 * address of the client which publish the file
 */
public class FileDescription implements Serializable {

	public static final long serialVersionUID = 5184014079797152383L;
	
	public String clientIPAddress;
	public String fileName;
	public int fileSize;
	public int firstFragmentSize;
	
	public FileDescription(String ipAdd, String fileName, int fileSize, int firstFragmentSize){
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.firstFragmentSize = firstFragmentSize;
		this.clientIPAddress = ipAdd;
	}

	@Override
	public String toString() {
		return "FileDescription [clientIPAddress=" + clientIPAddress + ", fileName=" + fileName + ", fileSize="
				+ fileSize + ", firstFragmentSize=" + firstFragmentSize + "]";
	}
	

}
