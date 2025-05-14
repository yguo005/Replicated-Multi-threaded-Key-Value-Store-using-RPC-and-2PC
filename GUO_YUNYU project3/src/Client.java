import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * The Client class represents a client that connects to multiple KeyValueRPC servers.
 */
public class Client {

  private KeyValueRPC[] servers;
  private Random random = new Random();
  private BufferedReader input;

  /**
   * Constructs a new Client that connects to the specified servers.
   *
   * @param addresses The addresses of the servers.
   * @param ports The ports of the servers.
   * @throws IllegalArgumentException If the wrong number of addresses or ports are provided.
   */
  public Client(String[] addresses, int[] ports) {
    if (addresses.length != 5 || ports.length != 5) {
      throw new IllegalArgumentException("Exactly 5 server addresses and ports must be provided");
    }

    servers = new KeyValueRPC[5];
    for (int i = 0; i < 5; i++) {
      try {
        Registry registry = LocateRegistry.getRegistry(addresses[i], ports[i]);
        servers[i] = (KeyValueRPC) registry.lookup("KeyValueRPC");
        log("Connected to server at " + addresses[i] + ":" + ports[i]);
      } catch (Exception e) {
        log("Client exception when connecting to server: " + addresses[i] + ":" + ports[i]);
        e.printStackTrace();
        // Exit if can't connect to all servers
        System.exit(1);
      }
    }

    input = new BufferedReader(new InputStreamReader(System.in));

    // Add a delay to allow servers to fully set up
    System.out.println("Waiting for servers to set up...");
    try {
      //wait for 30 sec
      for (int i =0; i<30;i++)
        if (allServersReady()){
          break;
        }
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (!allServersReady()){
      System.out.println("Not all servers are ready. Exiting.");
      System.exit(1);
    }

    // Pre-populate the Key-Value store with data and a set of keys
    System.out.println("Pre-populating key-value store...");
    for (int i = 1; i <= 5; i++) {
      sendCommand("PUT key" + i + " " +"value" + i);
    }

    // Verify pre-population
    System.out.println("Verifying pre-populated data...");
    for (int i = 1; i <= 5; i++) {
      sendCommand("GET key" + i);
    }

    // Start reading interactive command loop
    String line = "";
    while (!line.equalsIgnoreCase("QUIT")) {
      try{
          System.out.println(
          "Enter a command: PUT key value, GET key, DELETE key, GETALL or 'QUIT' to quit: ");
          line = input.readLine();
          if (!line.equalsIgnoreCase("QUIT")) {
            sendCommand(line);
      }
    } catch(Exception e){
      log("Error reading command: " + e.getMessage());
    }
  }

  System.out.println("Client shutting down...");
}

  /**
   * Checks if all servers are ready to receive commands.
   *
   * @return true if all servers are ready, false otherwise.
   */
private boolean allServersReady(){
    for (KeyValueRPC server : servers){
      try{
        server.getAll();
      } catch (Exception e){
        log("Server not ready: " + e.getMessage());
        return false;
      }
    }
    return true;
}

  /**
   * Sends a command to a random server and handles the response.
   *
   * @param command The command to send.
   */
private void sendCommand(String command) {
    try {
      String[] parts = command.split(" ");
      if (parts.length ==0){
        log("Invalid command format");
        return;
      }

      KeyValueRPC server = servers[random.nextInt(5)];
      String response = "";

      switch (parts[0].toUpperCase()) {
        case "PUT":
          if (parts.length != 3) {
            log("Invalid PUT command. Usage: PUT key value");
            return;
          }
          response = server.put(parts[1], parts[2]);
          break;
        case "GET":
          if (parts.length != 2) {
            log("Invalid GET command. Usage: GET key");
            return;
          }
          response = server.get(parts[1]);
          break;
        case "DELETE":
          if (parts.length != 2) {
            log("Invalid DELETE command. Usage: DELETE key");
            return;
          }
          response = server.delete(parts[1]);
          break;
        case "GETALL":
          response = server.getAll();
          break;
        default:
          log("Unknown command: " + parts[0]);
          return;
      }
      log("Received from server: " + response);
    } catch (Exception e) {
      log("Error executing command: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Logs a message with a timestamp.
   *
   * @param message The message to log.
   */
  private static void log(String message) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String timestamp = LocalDateTime.now().format(formatter);
    System.out.println(timestamp + ": " + message);
  }

  /**
   * The main method for the Client class.
   *
   * @param args Command line arguments, which should include the addresses and ports of the servers.
   */
  public static void main(String args[]) {
    if (args.length != 10) {
      log("Usage: java Client <host1> <port1> <host2> <port2> <host3> <port3> <host4> <port4> <host5> <port5>");
      System.exit(1);
    }

    String[] hosts = new String[5];
    int[] ports = new int[5];
    for (int i = 0; i < 5; i++) {
      hosts[i] = args[i * 2];
      try {
        ports[i] = Integer.parseInt(args[i * 2 + 1]);
      } catch (NumberFormatException e) {
        log("ERROR: port number must be integer");
        System.exit(1);
        return;
      }
    }
    new Client(hosts, ports);

  }
}
