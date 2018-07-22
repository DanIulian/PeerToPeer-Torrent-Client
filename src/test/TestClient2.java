package test;

import java.net.InetAddress;
import client.Client;

public class TestClient2 {

	public static String fileName1 = "Compilers.pdf";
	public static String fileName2 = "porche.jpg";
	
	public static void main(String argv[]) {
		
		System.out.println("[TestClient2]: Starting testing client 2 (this one retreive a file).");
		if (argv.length < 2) {	
			System.out.println("[TestClient2]: The client starts with command: java Client ServerHost ServerPort ClientPort");
			System.exit(-1);
		} 		
		try {
			
			/* adresarea serverului (ip, port) */
			String serverHost = argv[0];
			int serverPort = Integer.parseInt(argv[1]);
			
			/* adresarea clientului (ip, port) */
			String clientHost = InetAddress.getLocalHost().getHostName();
			int clientPort = Integer.parseInt(argv[2]);

			Client cli = new Client(clientHost,clientPort,serverHost,serverPort);
			cli.retrieveFile(fileName1);
			System.out.println("[TestClient2]: File " + fileName1 + " was retrieved... DONE!");

			Thread.sleep(20000);
			
			cli.retrieveFile(fileName2);
			System.out.println("[TestClient2]: File " + fileName2 + " was retrieved... DONE!");
			
			Thread.sleep(60000);
			System.out.println("Closing client");
			cli.close();
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}	
}