package central_node_pkg;

import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

import java.util.Iterator;


import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import org.apache.log4j.*;
import comm_proto.FileDescription;
import comm_proto.FragmentDescription;

/*
 * This class implements the central node server for the application
 * Using a selector, it accepts new connections and new messages from clients
 * Once a message is received, it processes it doing one of the following actions:
 * 	1. Create a new published file - the client just published a new file
 * 	2. Add a new client to a fragment of a file - a new client just downloaded that fragment
 *  3. Transmit to a client a list of all clients that have fragments of a certain file - the client
 *  wants to download the file
 * 
 * When communicating to clients, it uses a message that has a fixed header which contains information
 * about the type of the message, the length of the message content and also the port of 
 * the client who received the message.
 */

public class CentralNode implements Runnable{
	
	private static final int PUBLISHFILE_MSG = 0;
	private static final int PUBLISHFRAG_MSG = 1;
	private static final int GETCLIENTS_MSG = 2;
	private static final int SENDCLIENTS_MSG = 3;
	
	private static final int TYPE_INDEX = 0;
	private static final int LENGTH_INDEX = 4;
	private static final int PORT_NR_INDEX = 8;
	private static final int DATA_INDEX = 12;
	private static final int HEADER_SIZE = 12;
	
	private static final Logger logger = Logger.getLogger(CentralNode.class);
	
	private static final int BUF_SIZE = 131072;
	
	private final int port;
	private String ip;

	private ServerSocketChannel conSock;
	private Selector selector;
	private ByteBuffer message;
	private HashMap<String, PublishedFile> publishedFiles;
	
	public CentralNode(int port, String ip){
		
		logger.info("Starting server ... ");
		
		this.publishedFiles = new HashMap<String, PublishedFile>();
		this.port = port;
		this.ip = ip;
		this.message = ByteBuffer.allocate(BUF_SIZE);
		
		try {
			this.conSock = ServerSocketChannel.open();
			this.conSock.bind(new InetSocketAddress(ip, this.port));
			this.conSock.configureBlocking(false);
			
			this.selector = Selector.open();
			this.conSock.register(this.selector, SelectionKey.OP_ACCEPT);
			
		} catch (IOException e) {
			logger.error("Something went wrong when trying to boot up the server", e);
		}
	}

	public void run(){
		
		logger.info("Server up and running. Server listening on IP "
				+ this.ip + " and port " + this.port);
		
		while(true){
			try{
				
				this.selector.select();
				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
				
				while(selectedKeys.hasNext()){
					SelectionKey key = (SelectionKey)selectedKeys.next();
					selectedKeys.remove();
					
					if (!key.isValid())
						continue;
					
					if (key.isAcceptable()){
						acceptNewConnection(key);
					}
					
					else if (key.isReadable()){
						readIncommingMessage(key);	
					}
				}
			} catch(IOException e){
				logger.error("Something went wrong when calling select", e);
			}	
		}
	}
	
	private void acceptNewConnection(SelectionKey key)  throws IOException{
		
		logger.info("Incoming new connection");
		
		SocketChannel cl_sock = this.conSock.accept();
		cl_sock.configureBlocking(false);
		cl_sock.register(this.selector, SelectionKey.OP_READ);
		
		logger.info("Accepted new connection from "
				+ cl_sock.getRemoteAddress().toString()
				+ " on port " + cl_sock.socket().getPort());
	}

	private void readIncommingMessage(SelectionKey key) throws IOException{
		
		int bytes_read = 0, new_read = 0;
		int message_type = 0, message_length = 0;
		int client_port = 0;
		
		SocketChannel cur_sock = (SocketChannel)key.channel();
		Socket client_info = cur_sock.socket();
		
		logger.info("Incoming new message from "
				+ cur_sock.getRemoteAddress().toString() 
				+  " on port " + client_info.getPort());

		this.message.clear();
		this.message.position(0);
		
		//get header and extract information from it
		while(bytes_read < HEADER_SIZE){
			new_read = cur_sock.read(message);
			if (new_read == -1){
				cur_sock.close();
				key.cancel();
				logger.info(cur_sock.getRemoteAddress().toString() + " has closed connection...");
				return;
			}
			bytes_read += new_read;
		}

		message_type = this.message.getInt(TYPE_INDEX);
		message_length = this.message.getInt(LENGTH_INDEX);
		client_port = this.message.getInt(PORT_NR_INDEX);
		
		byte[] dataMessage = new byte[message_length];
		message_length -= (message.position() - HEADER_SIZE);

		while(message_length > 0){
			new_read = cur_sock.read(message);
			if (new_read == -1){
				cur_sock.close();
				key.cancel();
				logger.info(cur_sock.getRemoteAddress().toString() + " has closed connection...");
				return;
			}
			message_length -= new_read;
		}
		
		this.message.position(DATA_INDEX);
		message.get(dataMessage, 0, dataMessage.length);
		
		this.processMessage(message_type, dataMessage, client_port, cur_sock);
	}

	
	private void sendACK(SocketChannel sock , InetSocketAddress client_info){
		
		int bytesSend = 0, messageLength;
		byte[] message_content;
		ByteBuffer sendMessage;
		
		logger.info("Send ACK message to " + client_info.getHostName());

		this.message.clear();
		this.message.position(0);
		
		this.message.putInt(TYPE_INDEX, SENDCLIENTS_MSG);
		this.message.putInt(LENGTH_INDEX, 0);
		this.message.putInt(PORT_NR_INDEX, client_info.getPort());
		messageLength = HEADER_SIZE;

		message_content = new byte[messageLength];
		this.message.position(0);
		this.message.get(message_content, 0, messageLength);
		sendMessage = ByteBuffer.wrap(message_content);
		try{
			while(messageLength > 0){
				bytesSend = sock.write(sendMessage);
				messageLength -= bytesSend;
			}
		} catch(IOException e){
			logger.error("Something went wrong when trying to send ACK to " + client_info.getHostName(),
					e);
		}
	}
	
	private void processMessage(int message_type, byte[] message, int client_port, SocketChannel cur_sock) {
		
		ByteArrayInputStream bis = new ByteArrayInputStream(message);
		FileDescription fDes;
		FragmentDescription fragDes;
		String fileName;
		ObjectInputStream ois = null;
		int bytesSend = 0;
		int messageLength;
		byte[] message_content;
		
		try{
			ois = new ObjectInputStream(bis);

			if (message_type == PUBLISHFILE_MSG){
				fDes = (FileDescription)ois.readObject();
				
				InetSocketAddress client_info = new InetSocketAddress(fDes.clientIPAddress, client_port);
				PublishedFile pFile = new PublishedFile(fDes.fileName, fDes.fileSize, fDes.firstFragmentSize, client_info);
				
				logger.info("Message received from " + client_info.getHostName() + "about " + fDes);
				
				this.publishedFiles.put(new String(fDes.fileName), pFile);
				this.sendACK(cur_sock, client_info);
				
			}
			
			else if (message_type == PUBLISHFRAG_MSG){
				fragDes = (FragmentDescription)ois.readObject();
		
				InetSocketAddress client_info = new InetSocketAddress(fragDes.clientIpAddress, client_port);
				PublishedFile pFile = this.publishedFiles.get(fragDes.fileName);
				pFile.addNewClient(fragDes.fragmentNumber, client_info);
			
				logger.info("Message received from " + client_info.getHostName() + "about " + fragDes);
				
				this.sendACK(cur_sock, client_info);
			}
			
			else if (message_type == GETCLIENTS_MSG){
				fileName = (String)ois.readObject();
				
				logger.info("Message received requesting information about " + fileName );

				this.message.clear();
				this.message.position(0);
				
				//there is no such file
				if (!this.publishedFiles.containsKey(fileName)){
					this.message.putInt(TYPE_INDEX, SENDCLIENTS_MSG);
					this.message.putInt(LENGTH_INDEX, 0);
					this.message.putInt(PORT_NR_INDEX, client_port);
					messageLength = HEADER_SIZE;
				}
				//the file exists
				else{
					this.message.putInt(TYPE_INDEX, SENDCLIENTS_MSG);
					message_content = this.publishedFiles.get(fileName).getClientsList();
					System.out.println(message_content.length);
					this.message.putInt(LENGTH_INDEX, message_content.length);
					this.message.putInt(PORT_NR_INDEX, client_port);
					
					this.message.position(DATA_INDEX);
					this.message.put(message_content, 0, message_content.length);
					
					messageLength = HEADER_SIZE + message_content.length;
				}

				
				ByteBuffer sendMessage;
				message_content = new byte[messageLength];
				this.message.position(0);
				this.message.get(message_content, 0, messageLength);
				sendMessage = ByteBuffer.wrap(message_content);
				try{
					while(messageLength > 0){
						bytesSend = cur_sock.write(sendMessage);
						messageLength -= bytesSend;
					}
				} catch(IOException e){
					logger.error(
							"Something went wrong when sending information to the client about the file requested", e);
				}
			}
			else
				logger.error("The message type is not known");
			
		} catch(IOException e){
			logger.error(
					"Something went wrong when trying to deserialize the data received from client", e);
		} catch(ClassNotFoundException e){
			logger.error(
					"Something went wrong when trying to deserialize the data received from client", e);
		}
		finally {
			  try
			  {
			    if (ois != null)
			      ois.close();
			    
			  } catch (IOException ex) {
			    ex.printStackTrace();
			  }
			}
		}

}
