package client;


import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.*;

/*
 * This class abstracts a file published by a client.
 * Each client in the network has a list of AvailableFile
 * The first purpose of this class is to split the file into fragments
 * so that each file is splitted the same no mater what client has it
 * Also when a client read or write a fragment to this file, it does so
 * using its methods, readFragment() and writeFragment() which are both 
 * Synchronized so that for a file all readings and writings are done sequentially
 * The class uses RandomAccessFile in order to write and read fragments out of order
 * It also has an array of offsets so that when a fragment is required it could compute faster
 * the fragment offset
 */
public class AvailableFile {
	
	private static final Logger loger = Logger.getLogger(AvailableFile.class);
	
	private int fragmentSize;

	private String fileName;
	private File fileObject;
	private int fileSize;
	private ArrayList<Integer> fragmentOffset;
	private Object lock = new Object();
	private RandomAccessFile f = null;
	
	public AvailableFile(File file, int fileSize) throws IOException{
		
		this.fileName = file.getName();
		this.fileObject = file;
		this.fragmentOffset = new ArrayList<Integer>();
		
		try{
			f = new RandomAccessFile(this.fileObject, "rwd");
			this.fileSize = (int)f.length();
			if (this.fileSize == 0)
				this.fileSize = fileSize;
			
			this.fragmentSize = this.splitFileAlgorithm();
			
			int nrFragments =  this.fileSize / this.fragmentSize;
			int partialOffset = 0;
			for(int i = 0; i < nrFragments; ++i){
				this.fragmentOffset.add(this.fragmentSize + partialOffset);
				partialOffset += this.fragmentSize;
			}
			if (this.fileSize % this.fragmentSize != 0)
				this.fragmentOffset.add(partialOffset + (this.fileSize % this.fragmentSize));
			
		} catch(IOException e){
			throw new IOException("File could not be accessed", e);
		}

	}
	
	
	private int splitFileAlgorithm(){
		
		int blockSize = 1024;
		
		if (this.fileSize < 128 * blockSize)
			return 2 * blockSize;
		
		if (this.fragmentSize < 512 * blockSize)
			return 8 * blockSize;
		
		if (this.fragmentSize < blockSize * blockSize)
			return 32 * blockSize;
		
		if (this.fileSize < 128 * blockSize * blockSize)
			return 128 * blockSize;
		
		return 512 * blockSize;
	}	
	
	
	public byte[] readFragment(int fragmentNumber){
		
		int offset;
		int fragmentSize;
		byte[] fragment = null;
		
		if (fragmentNumber >= this.fragmentOffset.size())
			return null;
		
		if (fragmentNumber == 0){
			offset = 0;
			fragmentSize = this.fragmentOffset.get(0);
		}
		else {
			offset = this.fragmentOffset.get(fragmentNumber - 1);
			fragmentSize = this.fragmentOffset.get(fragmentNumber) - offset;
		}
		fragment = new byte[fragmentSize];
		synchronized(this.lock){
			
			try{
				if (this.f == null)
					this.f = new RandomAccessFile(this.fileObject, "rwd");
				this.f.seek(offset);
				this.f.readFully(fragment, 0, fragmentSize);
			
			} catch(IOException e){
				this.closeFile();
				loger.error(
						"Something went wrong when reading fragment " + fragmentNumber
						+ " from file "  + this.fileName, e);
			}
		}
		return fragment;
		
	}
	
	public Boolean writeFragment(int fragmentNumber, byte[] fragment){
		
		int offset;
		int fragmentSize;
		Boolean rezultat = true;
		
		if (fragmentNumber >= this.fragmentOffset.size())
			return false;
		
		if (fragmentNumber == 0){
			offset = 0;
			fragmentSize = this.fragmentOffset.get(0);
		} else{
			offset = this.fragmentOffset.get(fragmentNumber - 1);
			fragmentSize = this.fragmentOffset.get(fragmentNumber) - offset;
		}
		
		if (fragmentSize != fragment.length)
			return false;

		synchronized(this.lock)
		{
			try{
				if (this.f == null)
					this.f = new RandomAccessFile(this.fileObject, "rwd");
				this.f.seek(offset);
				this.f.write(fragment, 0, fragmentSize);
			} catch (IOException e){
				this.closeFile();
				loger.error(
						"Something went wrong when writing fragment " + fragmentNumber
						+ " to file "  + this.fileName, e);
				rezultat = false;
	
			}
		}

		return rezultat;
	}
	
	public int getNrFragments(){
		return this.fragmentOffset.size();
	}
	
	public int getFileSize(){
		return this.fileSize;
	}
	
	public File getFileObject(){
		return this.fileObject;
	}
	
	public int getFragmentSize(int fragmentNumber){
		
		if (fragmentNumber == 0)
			return this.fragmentOffset.get(0);
		else
			return this.fragmentOffset.get(fragmentNumber) - this.fragmentOffset.get(fragmentNumber -1 );
	}
	
	public synchronized void closeFile(){
		try{
			if (this.f != null)
				this.f.close();
			this.f = null;
		}catch(IOException e){
			loger.error(
					"Something went wrong when closing file " + this.fileName, e);
		}
	}
}
