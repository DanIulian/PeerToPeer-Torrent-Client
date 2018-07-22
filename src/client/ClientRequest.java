package client;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.util.HashMap;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

import org.apache.log4j.*;

/*
 * Objects of this class are used with the thread pool. Everytime 
 * a peer request comes, an object of type ClientRequest is dispatched
 * that process that request, send the required fragment and close the connection
 * afterwards.
 */


public class ClientRequest implements Runnable{


	private static final int LENGTH_INDEX = 0;
	private static final int HEADER_SIZE = 4;
	private static final int DATA_INDEX = 4;
	
	private static final Logger logger = Logger.getLogger(ClientRequest.class);

	private static final int BUF_SIZE = 131072;
	
	private SocketChannel sock = null;
	private HashMap<String, AvailableFile> accessibleFiles;
	private ByteBuffer message;
	private String clientIp = null;
	private int clientPort = 0;

	public ClientRequest(SocketChannel s, HashMap<String, AvailableFile> af){
		this.sock = s;
		this.accessibleFiles = af;
		this.message = ByteBuffer.allocate(BUF_SIZE);
	}
	
	public void run(){
		
		int bytes_read = 0, new_read = 0;
		int message_length = 0;

		this.message.clear();
		this.message.position(0);
		try{
			this.clientIp = this.sock.getRemoteAddress().toString();
			this.clientPort = this.sock.socket().getPort();
			while(bytes_read < HEADER_SIZE){
				new_read  = this.sock.read(message);
				if (new_read == -1){
					try{
						this.sock.close();
						this.sock = null;
					} catch(IOException e){
						logger.error(
								"Something went wrong when closing socket from "
								+ this.clientIp +"/" + this.clientPort, e);
					}
					return;
				}
				bytes_read += new_read;
			}
		
			message_length = this.message.getInt(LENGTH_INDEX);

			byte[] dataMessage = new byte[message_length];
		
			message_length -= (message.position() - HEADER_SIZE);
		
			while(message_length > 0){
				new_read = this.sock.read(message);
				if (new_read == -1){
					try{
						this.sock.close();
						this.sock = null;
					} catch(IOException e){
						logger.error(
								"Something went wrong when closing socket from "
								+ this.clientIp +"/" + this.clientPort, e);
					}
					return;
				}	
				message_length -= new_read;
			}
			message.position(DATA_INDEX);
			message.get(dataMessage, 0, dataMessage.length);

			this.processMessage(dataMessage);
		} catch(IOException e){
			logger.error("Something went wrong when processing message", e);
		} finally{
			if (this.sock != null)
				try{
					this.sock.close();
				} catch(IOException ee){
					logger.error("Something went wrong when closing socket", ee);
				}	
		}
	}

	private void processMessage(byte[] dataMessage) {
		
		ByteArrayInputStream bis = new ByteArrayInputStream(dataMessage);
		FragmentRequest fragReq = null;
		byte[] fragment;
		ObjectInputStream ois = null;
		Boolean rez = true;
		ByteBuffer sendMessage;
		
		try{
			ois = new ObjectInputStream(bis);
			fragReq = (FragmentRequest)ois.readObject();
		} catch(IOException e){
			rez = false;
			logger.error(
					"Something went wrong when deserializing message from"
					+ this.clientIp +"/" + this.clientPort, e);
		} catch(ClassNotFoundException e){
			rez = false;
			logger.error(
					"Something went wrong when deserializing message from"
					+ this.clientIp +"/" + this.clientPort, e);
		}
		finally 
		{
			try {
				if (ois != null)
			      ois.close(); 
			  } catch (IOException ex) {
					logger.error(
							"Something went wrong when deserializing message from"
							+ this.clientIp +"/" + this.clientPort, ex);
			  }
			if (rez == false)
				return ;
		}
		
		logger.info("Client " + this.clientIp +"/" + this.clientPort + "requested " + fragReq);

		if (fragReq != null){
			
			AvailableFile af = this.accessibleFiles.get(fragReq.fileName);
			if (af != null){
				
				fragment = af.readFragment(fragReq.fragmentNumber);
				if (fragment != null){
					int fragmentSize = fragment.length;
					int nrBytesSend = 0;
					sendMessage = this.getSendingMessage(fragmentSize, fragment);
					try{
						while(fragmentSize > 0){
							nrBytesSend = this.sock.write(sendMessage);
							fragmentSize -= nrBytesSend;

						}
						logger.info("Fragment " + fragReq +" sent to " + this.clientIp + "/" + this.clientPort);
					} catch(IOException e){
						logger.error(
								"Something went wrong when sending"
								+ fragReq +" to " + this.clientIp + "/" + this.clientPort);
					}
				} 
				else
					logger.error("Fragment requested:" + fragReq + " is null");
			}
			else
				logger.error("File requested " + fragReq + " does not exist");
		}
	}
	
	private ByteBuffer getSendingMessage(int messageLength, byte[] content){
		return ByteBuffer.wrap(content);
	}
}
