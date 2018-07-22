package client;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

import org.apache.log4j.*;

import comm_proto.FragmentFile;

/*
 * Objects of this class are responsible for downloading a fragment for a file
 * Each AskForFragment object is dispatched in a pool of threads when a file is
 * downloaded.
 * When trying to download a file, this object tries each client that the
 * central node said that has the file until one actually gives the file, or
 * until all fails signaling the client this thing.
 */

public class AskForFragment implements Runnable {
	
	private static final int LENGTH_INDEX = 0;
	private static final int HEADER_SIZE = 4;
	private static final int DATA_INDEX = 4;
	
	private static final Logger logger  = Logger.getLogger(AskForFragment.class);
	private static final int BUF_SIZE = 131072;
	
	private int fragmentNr, sendMessageLength;
	private String fileName;
	private FragmentFile fragment;
	private Client client;
	private AvailableFile aFile;
	private List<Boolean> downloadedFragments;
	private ByteBuffer send_message, receive_message;
	
	public AskForFragment(String fileName, int fragmentNr, FragmentFile fragment,
			AvailableFile aFile, Client client, List<Boolean> downloadedFragments){
	
		this.fileName = fileName;
		this.fragment = fragment;
		this.fragmentNr = fragmentNr;
		this.client = client;
		this.aFile = aFile;
		this.downloadedFragments = downloadedFragments;
		this.receive_message = ByteBuffer.allocate(BUF_SIZE);
		
		//compune mesajul pe care il vom trimite la toti clientii cerand fragmentul
		this.send_message = this.composeSendMessage();
	}
	/*
	 * compose the message requesting the file so that it could be send to different
	 * clients without having to build it again
	 */
	private ByteBuffer composeSendMessage(){
		
		ByteBuffer aux = ByteBuffer.allocate(BUF_SIZE);
		byte[] message_content = this.serializeFragmentRequest();
		
		aux.clear();
		aux.position(0);
		aux.putInt(LENGTH_INDEX, message_content.length);
		
		aux.position(DATA_INDEX);
		aux.put(message_content, 0, message_content.length);
		
		this.sendMessageLength = message_content.length + HEADER_SIZE;
		
		message_content = new byte[this.sendMessageLength];
		aux.position(0);
		aux.get(message_content, 0, message_content.length);
		
		return ByteBuffer.wrap(message_content);
	}
	
	
	private byte[] serializeFragmentRequest(){

		FragmentRequest fReq = new FragmentRequest(this.fragmentNr, this.fileName);

		ByteArrayOutputStream bos  = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		byte[] serializedMessage = null;
		
		try{
			oos = new ObjectOutputStream(bos);
			oos.writeObject(fReq);
			oos.flush();
			serializedMessage = bos.toByteArray();
		} catch(IOException e){
			logger.error("Something went wrong when composing the request fragment message for" + fReq, e);
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

	@Override
	public void run() {
		
		logger.info("Try downloading fragment " + this.fragmentNr + " for file " + this.fileName);
		
		Boolean ok;
		ArrayList<InetSocketAddress> possibleClients = this.fragment.getClients();
		Collections.shuffle(possibleClients);
		
		//the fragment is downloaded if the download takes place and the fragment is published successfully
		//try each client until one succeed or all fails
		for(InetSocketAddress clAdd: possibleClients){
			if (downloadedFragment(clAdd)){
				ok = this.client.signalCentralNode(this.fileName, this.fragmentNr);
				if (ok){
					this.downloadedFragments.set(this.fragmentNr, true);
					logger.info("Download Successful: Fragment " + this.fragmentNr + " from file " + this.fileName);
					break;
				}
			}
		}

	}
	
	private Boolean downloadedFragment(InetSocketAddress clientAddress){
		
		int bytesSend, bytesReceived;
		int messageLength;
		Boolean rez;
		byte[] fragment_content = new byte[this.fragment.getFragmentSize()];
		SocketChannel sock = null;
		
		try {
			sock = SocketChannel.open();
			sock.connect(clientAddress);
			sock.configureBlocking(false);
		} catch (IOException e) {
			
			logger.info(
					"Could not connect to clinet" + clientAddress
					+ " in order to download fragment" + this.fragmentNr 
					+ " from file" + this.fileName);
			
			if (sock != null)
				try{
					sock.close();
					sock = null;
				} catch(IOException ee){
					ee.printStackTrace();
				}
			return false;
		}
		
		//connection established, now ask the client for the fragment
		bytesSend = 0;
		messageLength = this.sendMessageLength;
		this.send_message.position(0);
		try
		{
			while(messageLength > 0){
				bytesSend = sock.write(this.send_message);
				messageLength -= bytesSend;
			}
		} catch(IOException e){
			logger.info(
					"Error sending request to client" + clientAddress
					+ " in order to download fragment" + this.fragmentNr 
					+ " from file" + this.fileName);
			if (sock != null)
				try{
					sock.close();
					sock = null;
				} catch(IOException ee){
					logger.error("Error when closing connection with client " + clientAddress, e);
				}
			return false;
		}

		//the request was send, wait for answer
		this.receive_message.clear();
		this.receive_message.position(0);
		bytesReceived = 0;
		messageLength = this.fragment.getFragmentSize();
		try{
			while(bytesReceived != -1){
				messageLength -= bytesReceived;
				bytesReceived = sock.read(this.receive_message);
			}
		} catch(IOException e){
			logger.info(
					"Something went wrong when waiting the answer from " + clientAddress
					+ " in order to download fragment" + this.fragmentNr 
					+ " from file" + this.fileName);
			if (sock != null)
				try{
					sock.close();
					sock = null;
				} catch(IOException ee){
					logger.error("Error when closing connection with client " + clientAddress, e);
				}
			return false;
		}
		if (messageLength != 0){
			
			logger.info(
					"The client " + clientAddress
					+ " closed connection when download fragment" + this.fragmentNr 
					+ " from file" + this.fileName);
			if (sock != null)
				try{
					sock.close();
					sock = null;
				} catch (IOException e){
					logger.error("Error when closing connection with client " + clientAddress, e);
				}
			return false;
		}
		if (sock != null)
			try{
				sock.close();
				sock = null;
			} catch (IOException e){
				logger.error("Error when closing connection with client " + clientAddress, e);
				return false;
			}
		// the fragment was received, write it in the file and publish it
		this.receive_message.position(0);
		this.receive_message.get(fragment_content);
		rez = this.aFile.writeFragment(this.fragmentNr, fragment_content);

		return rez;
			
	}
	
}
