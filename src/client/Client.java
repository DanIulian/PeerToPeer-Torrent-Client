package client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import java.io.FileOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import java.net.InetSocketAddress;


import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.nio.file.*;

import org.apache.log4j.*;

import comm_proto.FileDescription;
import comm_proto.FragmentDescription;
import comm_proto.FragmentFile;

public class Client extends AbstractClient{
	
	private static final int PUBLISHFILE_MSG = 0;
	private static final int PUBLISHFRAG_MSG = 1;
	private static final int GETCLIENTS_MSG = 2;
	
	private static final int TYPE_INDEX = 0;
	private static final int LENGTH_INDEX = 4;
	private static final int PORT_NR_INDEX = 8;
	private static final int DATA_INDEX = 12;
	private static final int HEADER_SIZE = 12;
	
	private static final Logger logger = Logger.getLogger(Client.class);
	private static final int BUF_SIZE = 131072;
	
	private String clientDirName;
	
	private final int port;
	private String ip;
	
	private final int centralNodePort;
	private String centralNodeIP;

	private ServerSocketChannel conSock = null;;
	private SocketChannel centralNodeSock = null;
	
	private Selector selector = null;
	private ByteBuffer message;

	private HashMap<String, AvailableFile> accessibleFiles;
	
	private ClientAsServer cas;
	private Thread cas_thread;
	
	
	public Client(String clientHost, int clientPort, String serverHost, int serverPort) {
		File clientDir = null;
		
		this.accessibleFiles = new HashMap<String, AvailableFile>();
		this.port = clientPort;
		this.ip = clientHost;
		this.centralNodePort = serverPort;
		this.centralNodeIP = serverHost;
		this.message = ByteBuffer.allocate(BUF_SIZE);
		
		
		//check for specific directory and stuff;
		this.clientDirName = "Client_" + Integer.toString(this.port);
		
		clientDir = new File(this.clientDirName);
		if (!clientDir.exists())
		{
			logger.info("New client ... create directory " + this.clientDirName);
			try{
				Path clientDirPath = Paths.get(this.clientDirName);
				Files.createDirectory(clientDirPath);
			} catch(IOException e){
				logger.error("Could not create directory for client " + this.clientDirName);
			}
			
		} 
		else
		{
			File[] directoryFiles = clientDir.listFiles();
			if (directoryFiles != null)
				for (File publishedFile : directoryFiles)
				{
					try
					{
						String fileName = publishedFile.getName();
						AvailableFile af = new AvailableFile(publishedFile, 0);
						this.accessibleFiles.put(fileName, af);
					} catch(IOException e){
						logger.error(
								"Error when creating interface for pubished file " + publishedFile.getName(), e);
					}
				}
		}
		
		try {
			
			this.conSock = ServerSocketChannel.open();
			InetSocketAddress aiaa = new InetSocketAddress(this.ip, this.port);
			this.conSock.bind(aiaa);
			
			logger.info("Accepting download requests on " + aiaa);

			this.conSock.configureBlocking(false);
			
			this.selector = Selector.open();			
			this.conSock.register(this.selector, SelectionKey.OP_ACCEPT);
			
			this.centralNodeSock = SocketChannel.open();
			this.centralNodeSock.connect(new InetSocketAddress(this.centralNodeIP, this.centralNodePort));
			this.centralNodeSock.configureBlocking(false);
			
			this.cas = new ClientAsServer(this.conSock, selector, accessibleFiles);
			this.cas_thread = new Thread(this.cas);
			this.cas_thread.start();
			
		} catch (IOException e) {
			if (this.conSock != null)
				try{
					this.conSock.close();
				} catch(IOException ee){
					
					ee.printStackTrace();
				}
			if (this.selector != null)
				try {
					this.selector.close();
				} catch (IOException e1) {

					e1.printStackTrace();
				}
			if (this.centralNodeSock != null)
				try {
					this.centralNodeSock.close();
				} catch (IOException e1) {
					
					e1.printStackTrace();
				}
			logger.error(
					"Something went wrong when trying to open connections for download and to central node",
					e);
		}
	}

	
	public byte[] serializeMessage(FileDescription fileDesc){
		
		ByteArrayOutputStream bos  = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		byte[] serializedMessage = null;
		
		try{
			oos = new ObjectOutputStream(bos);
			oos.writeObject(fileDesc);
			oos.flush();
			serializedMessage = bos.toByteArray();
		} catch(IOException e){
			logger.error("Something went wrong when serializing " + fileDesc, e);
		} finally {
			try{
				if (oos != null)
					oos.close();
			} catch(IOException ex){
				ex.printStackTrace();
			}
		}
		return serializedMessage;
	}
	
	
	public byte[] serializeMessage(FragmentDescription fragDesc){
		
		ByteArrayOutputStream bos  = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		byte[] serializedMessage = null;
		
		try{
			oos = new ObjectOutputStream(bos);
			oos.writeObject(fragDesc);
			oos.flush();
			serializedMessage = bos.toByteArray();
		} catch(IOException e){
			
			logger.error("Something went wrong when serializing " + fragDesc, e);
		} finally {
			try{
				if (oos != null)
					oos.close();
			} catch(IOException ex){
				ex.printStackTrace();
			}
		}
		return serializedMessage;
		
	}
	
	public byte[] serializeMessage(String fileName){
		
		ByteArrayOutputStream bos  = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		byte[] serializedMessage = null;
		
		try{
			oos = new ObjectOutputStream(bos);
			oos.writeObject(fileName);
			oos.flush();
			serializedMessage = bos.toByteArray();
		} catch(IOException e){
			logger.error("Something went wrong when serializing " + fileName, e);
		} finally {
			try{
				if (oos != null)
					oos.close();
			} catch(IOException ex){
				ex.printStackTrace();
			}
		}
		return serializedMessage;
		
	}
	
	/*
	 * Compose message for central node. This message can publish a file,
	 * add a new fragment, or ask for information about a file
	 */
	private synchronized int compose_message(int message_type,
			String filename, FileDescription fileDes, FragmentDescription fragDes)
	{
		byte[] message_content;
		
		this.message.clear();
		this.message.position(0);
		
		if (filename != null)
			message_content = this.serializeMessage(filename);
		else if (fileDes != null)
			message_content = this.serializeMessage(fileDes);		
		else
			message_content = this.serializeMessage(fragDes);
		
		this.message.putInt(TYPE_INDEX, message_type);

		this.message.putInt(LENGTH_INDEX, message_content.length);

		this.message.putInt(PORT_NR_INDEX, this.port);
		
		this.message.position(HEADER_SIZE);
		this.message.put(message_content, 0, message_content.length);
		
		this.message.position(0);
		
		return HEADER_SIZE + message_content.length;
	}
	
	/*
	 * return a ByteBuffer of length equal to the message length
	 */
	private synchronized ByteBuffer getRealMessage(int messageLength){
		
		byte[] realMessage = new byte[messageLength];
		this.message.position(0);
		this.message.get(realMessage, 0, messageLength);
		return ByteBuffer.wrap(realMessage);
	}
	
	
	@Override
	public void publishFile(File file) throws IOException {
		
		String fileName;
		File movedFile;
		FileInputStream fis = null;
		FileOutputStream fos = null;
		byte[] buffer = new byte[BUF_SIZE];
		int bytesRead;
		Boolean rez = true;
		IOException cur_e = null;

		//move the file into the client specific directory
		fileName = file.getName();
		if (!this.accessibleFiles.containsKey(fileName)){
			
			movedFile = new File(this.clientDirName + "/" + fileName);
			try{
				fis = new FileInputStream(file);
				fos = new FileOutputStream(movedFile);
				while((bytesRead = fis.read(buffer)) > 0) 
					fos.write(buffer, 0, bytesRead);
				
			} catch (IOException e){
				rez = false;
				cur_e = e;
			} finally{
				
				try{
					if (fis != null)
						fis.close();
					if (fos != null)
						fos.close();
				}  catch(IOException e){
					e.printStackTrace();
				}
				if (rez == false)
					throw new IOException("File could not be accessed", cur_e);
					
			}
			this.accessibleFiles.put(fileName,new AvailableFile(movedFile, 0));
		}
		
		// Assembly the package to be sent to central node with information about a
		// new published file
		AvailableFile pf = this.accessibleFiles.get(fileName);
		FileDescription newPublishedFile = new FileDescription(
				this.ip, fileName, pf.getFileSize(), pf.getFragmentSize(0));
		
		int messageLength = this.compose_message(PUBLISHFILE_MSG, null, newPublishedFile, null);
		int bytesSend = 0;
		ByteBuffer sendingMessage = this.getRealMessage(messageLength);

		try
		{
			while(messageLength > 0){
				bytesSend = this.centralNodeSock.write(sendingMessage);
				messageLength -= bytesSend;
				System.out.println(messageLength);
			}
		} catch(IOException e){
			throw new IOException("Error when publishing " + fileName + " to central node", e);
		}	
		this.receiveACK();
	}
	
	

	@SuppressWarnings("unchecked")
	@Override
	public File retrieveFile(String filename) throws IOException {
		
		byte [] message_content;
		int bytesSend, bytesRead, newRead, messageLength;
		ArrayList<FragmentFile> fragments = null;
		ByteArrayInputStream bis;
		ObjectInputStream ois = null;
		Boolean rez = true;
		ByteBuffer sendingMessage;
		
		//check if we don't already have the file
		if (this.accessibleFiles.containsKey(filename))
			return  this.accessibleFiles.get(filename).getFileObject();
		
		//send a message to central node asking for information about the file
		
		logger.info("Ask central node about file " + filename);

		
		messageLength = this.compose_message(GETCLIENTS_MSG, filename, null, null);
		sendingMessage = this.getRealMessage(messageLength);
		bytesSend = 0;
		try
		{
			while(messageLength > 0){
				bytesSend = this.centralNodeSock.write(sendingMessage);
				messageLength -= bytesSend;
			}
		} catch(IOException e){
			throw new IOException(
					"Error when asking  central node for clients that have the file " + filename, e);
		}
		
		
		// receive the message containing the information required
		bytesRead = 0;
		newRead = 0;	
		
		this.message.clear();
		this.message.position(0);
		
		while(bytesRead < HEADER_SIZE){
			newRead  = this.centralNodeSock.read(message);
			if (newRead == -1){
				throw new IOException("Server closed connection unexpected");
			}
			bytesRead += newRead;
		}

		messageLength = this.message.getInt(LENGTH_INDEX);		
		if (messageLength == 0)
			return null;

		message_content = new byte[messageLength];
		messageLength -= (message.position() - HEADER_SIZE);
		
		while(messageLength > 0){
			newRead = this.centralNodeSock.read(message);
			if (newRead == -1){
				throw new IOException("Server closed connection unexpected");
			}
			messageLength -= newRead;
		}
		
		logger.info("Received from server information about file " + filename);
	
		this.message.position(DATA_INDEX);
		this.message.get(message_content, 0, message_content.length);
		
		//deserializeaza informatia primita de la nodul central cu privire la clientii care au 
		//fragmente de fisier
		bis = new ByteArrayInputStream(message_content);
		rez = true;
		
		try{
			ois = new ObjectInputStream(bis);
			fragments = (ArrayList<FragmentFile>)ois.readObject();

		} catch(IOException e){
			rez = false;
		
		} catch (ClassNotFoundException e){
			rez = false;
		
		}finally{
			if (ois != null)
				try{
					ois.close();
				} catch(IOException e){
					e.printStackTrace();
				}
			if (rez == false)
				throw new IOException("Could not decode the clients list");
		}
		
		if (fragments == null)
			return null;
		
		return this.downloadFile(filename, fragments);
	}
	
	
	private File downloadFile(String fileName, ArrayList<FragmentFile> fragments) throws IOException{
		
		File downloadedFile;
		int fileSize = 0;
		AvailableFile aFile = null;
		List<Boolean> downloadedFragments; 
		ExecutorService pool;
		
		downloadedFile = new File(this.clientDirName + "/" + fileName);

		for(FragmentFile ff: fragments)
			fileSize += ff.getFragmentSize();
		
		aFile = new AvailableFile(downloadedFile, fileSize);
		
		logger.info("Start downloading file " + fileName + " of size " + fileSize);
		
		//a list that said which fragment was successfully download and which not
		downloadedFragments = Collections.synchronizedList(new ArrayList<Boolean>(aFile.getNrFragments()));
		this.accessibleFiles.put(fileName, aFile);
		for(int i = 0; i < aFile.getNrFragments(); ++i)
			downloadedFragments.add(false);
		
		pool = Executors.newFixedThreadPool(10);
		for (int i = 0; i < fragments.size() ; ++i){
			pool.submit(new AskForFragment(fileName, i, fragments.get(i), aFile, this, downloadedFragments));
		}
		
		pool.shutdown();
		while(!pool.isTerminated()){}

		for(int i = 0 ; i < fragments.size() ; ++i){
			if (downloadedFragments.get(i) == false)
				throw new IOException("Nu s-a putut descarca fragmentul" + Integer.toString(i));
		}
		
		logger.info("Download Successfull !, File " + fileName + " was download");
		aFile.closeFile();
		return downloadedFile;
	}


	public synchronized Boolean signalCentralNode(String fileName, int fragmentNr) {
		
		ByteBuffer sendedMessage;
		FragmentDescription frgDesc = new FragmentDescription(this.ip, fileName, fragmentNr);
		
		int messageLength = this.compose_message(PUBLISHFRAG_MSG, null, null, frgDesc);
		int bytesSend = 0;
		sendedMessage = this.getRealMessage(messageLength);
		try
		{
			while(messageLength > 0){
				bytesSend = this.centralNodeSock.write(sendedMessage);
				messageLength -= bytesSend;
			}
			this.receiveACK();
		} catch(IOException e){
			return false;
		}	
		return true;
	}
	
	
	private void receiveACK() throws IOException {
		
		// receive the message containing the information required
		int bytesRead = 0;
		int newRead = 0;	
		
		this.message.clear();
		this.message.position(0);
		
		while(bytesRead < HEADER_SIZE){
			newRead  = this.centralNodeSock.read(message);
			if (newRead == -1){
				throw new IOException("Server closed connection unexpected");
			}
			bytesRead += newRead;
		}
	}
	
	public void close(){
		
		this.cas.close();
		this.selector.wakeup();
		for (String fileName : this.accessibleFiles.keySet())
			this.accessibleFiles.get(fileName).closeFile();
		
		try{
			if (this.centralNodeSock != null)
				this.centralNodeSock.close();
			this.centralNodeSock = null;
			this.cas_thread.join();
			
		} catch (IOException e){
			e.printStackTrace();
		} catch (InterruptedException e){
			e.printStackTrace();
		}
		logger.info("Closing client ...");
	}
}
