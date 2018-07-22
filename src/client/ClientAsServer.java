package client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import org.apache.log4j.*;

/*
 * This class is responsible for serving requests to clients.
 * It runs on a different thread than the actual client, and waits for requests
 * to come on the Server Socket.
 * When a request comes, it dispatches using an Executor the request to a ClientRequest object
 * It can be closed from the main client server
 */

public class ClientAsServer implements Runnable {

	private static final Logger logger = Logger.getLogger(ClientAsServer.class);
	private static final int NR_THREADS = 10;

	private ExecutorService pool;
	private Selector selector;
	private ServerSocketChannel sockCon;
	private HashMap<String, AvailableFile> accessibleFiles;
	private volatile Boolean closingConnection = false; 
	
	
	public ClientAsServer(ServerSocketChannel s, Selector sel, HashMap<String, AvailableFile> aF){
		this.selector = sel;
		this.sockCon = s;
		this.accessibleFiles = aF;
		pool = Executors.newFixedThreadPool(NR_THREADS);
	}
	
	public void close(){
		this.closingConnection = true;
	}
	
	@Override
	public void run(){
		
		logger.info("Client starts accepting downloading requests");
		
		while(true){
			if (this.closingConnection){
				try{
					this.selector.close();
					this.sockCon.close();
				} catch(IOException e){
					logger.error("Something went wrong when trying to close the connection", e);
				}
				pool.shutdown();
				while(!pool.isTerminated()) {}
				
				logger.info("Client no longer accepts connection. Client closing ...");
				break;
			}
			try{
				this.selector.select();
				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
				
				while(selectedKeys.hasNext()){
					SelectionKey key = (SelectionKey)selectedKeys.next();
					selectedKeys.remove();
					
					if (!key.isValid())
						continue;
					
					if (key.isAcceptable())
						acceptNewConnection(key);

				}
			} catch(IOException e){
				logger.error("Something went wrong when trying to process new requests", e);
			}	
		}
	}
	
	private void acceptNewConnection(SelectionKey key)  throws IOException{
		
		SocketChannel cl_sock = this.sockCon.accept();
		cl_sock.configureBlocking(false);
		this.pool.submit(new ClientRequest(cl_sock, this.accessibleFiles));
	}
}
