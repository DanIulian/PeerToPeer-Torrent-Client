package central_node_pkg;

public class MainServer {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		if (args.length < 1)
			System.out.println("Running server with port number please");
		CentralNode server  = new CentralNode(Integer.parseInt(args[0]), "localhost");
		Thread t = new Thread(server);
		t.start();

	}

}
