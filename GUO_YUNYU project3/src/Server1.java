import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Server1 class represents a server that implements the KeyValueRPC interface.
 * It provides methods to connect to other servers, handle commands, and manage a key-value store.
 */
public class Server1 extends UnicastRemoteObject implements KeyValueRPC {

  private List<KeyValueRPC> otherServers = new ArrayList<>();
  private Map<String, String> store = new ConcurrentHashMap<>();
  private String[] otherServerAddresses;
  private int[] otherServerPorts;
  private static final Logger logger = Logger.getLogger(Server1.class.getName());
  private boolean isMaster;
  private int serverId;
  private int port;

  /**
   * Constructs a new Server1.
   *
   * @param port The port to run the server on.
   * @param otherServerAddresses The addresses of the other servers.
   * @param otherServerPorts The ports of the other servers.
   * @param isMaster Whether this server is the master server.
   * @param serverId The ID of this server.
   * @throws RemoteException If a remote communication error occurs.
   */
  public Server1(int port, String[] otherServerAddresses, int[] otherServerPorts, boolean isMaster, int serverId)
      throws RemoteException {
    super(port);
    this.port = port;
    this.isMaster = isMaster;
    this.serverId = serverId;
    if (otherServerAddresses.length != 4 || otherServerPorts.length != 4) {
      throw new IllegalArgumentException(
          "Exactly 4 other server addresses and ports must be provided");
    }
    this.otherServerAddresses = otherServerAddresses;
    this.otherServerPorts = otherServerPorts;
  }

  /**
   * Connects this server to the other servers.
   */
  private void connectToOtherServers() {
    for (int i = 0; i < otherServerAddresses.length; i++) {
      int retryCount = 0;
      boolean connected = false;
      while (retryCount < 5 && !connected) { // Increase max retries to 5
        try {
          Registry registry = LocateRegistry.getRegistry(otherServerAddresses[i],
              otherServerPorts[i]);
          KeyValueRPC otherServer = (KeyValueRPC) registry.lookup("KeyValueRPC");
          otherServers.add(otherServer);
          connected = true;
          logger.info("Successfully connected to server at " + otherServerAddresses[i] + ":" + otherServerPorts[i]);
        } catch (Exception e) {
          logger.log(Level.WARNING,
              "Error connecting to server " + otherServerAddresses[i] + ":" + otherServerPorts[i] + ". Retry " + (retryCount + 1), e);
          retryCount++;
          if (retryCount < 5) {
            try {
              Thread.sleep(10000); // Increase wait time to 10 seconds
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
      if (!connected) {
        logger.log(Level.SEVERE, "Failed to connect to server " + otherServerAddresses[i] + ":" + otherServerPorts[i] + " after 5 attempts.");
      }
    }
    // Check if all servers are connected
    if (otherServers.size() < 4) {
      logger.log(Level.SEVERE, "Not all servers are connected. Connected: " + otherServers.size() + "/4");
      System.exit(1);
    }
  }

  /**
   * Sends a PUT command to all servers and handles the responses.
   *
   * @param key The key to put.
   * @param value The value to put.
   * @return A string indicating the result of the operation.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public String put(String key, String value) throws RemoteException {
    if (prepareAllServers("PUT " + key + " " + value)) {
      store.put(key, value);
      commitAllServers("PUT " + key + " " + value);
      return "SUCCESS";
    } else {
      abortAllServers("PUT " + key + " " + value);
      return "ERROR: 2PC failed";
    }
  }

  /**
   * Retrieves the value associated with a key from the key-value store.
   *
   * @param key The key to get the value for.
   * @return The value associated with the key.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public String get(String key) throws RemoteException {
    String value = store.get(key);
    return (value != null) ? value : "ERROR: key not found";
  }

  /**
   * Sends a DELETE command to all servers and handles the responses.
   *
   * @param key The key to delete.
   * @return A string indicating the result of the operation.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public String delete(String key) throws RemoteException {
    if (prepareAllServers("DELETE " + key)) {
      store.remove(key);
      commitAllServers("DELETE " + key);
      return "SUCCESS";
    } else {
      abortAllServers("DELETE " + key);
      return "ERROR: 2PC failed";
    }
  }

  /**
   * Retrieves all key-value pairs from the key-value store.
   *
   * @return A string representation of all key-value pairs.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public String getAll() throws RemoteException {
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, String> entry : store.entrySet()) {
      result.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
    }

    // Aggregate from other servers
    for (KeyValueRPC otherServer : otherServers) {
      try {
        result.append(otherServer.getAll());
      } catch (RemoteException e) {
        logger.log(Level.WARNING, "Failed to get data from a server during GETALL", e);
      }
    }

    return result.toString();
  }

  private boolean prepareAllServers(String command) {
    ExecutorService executor = Executors.newFixedThreadPool(otherServers.size());
    List<Future<Boolean>> futures = new ArrayList<>();

    for (KeyValueRPC otherServer : otherServers) {
      futures.add(executor.submit(() -> {
        try {
          return "YES".equals(otherServer.prepare(command));
        } catch (RemoteException e) {
          logger.log(Level.SEVERE, "RemoteException while preparing command: " + command, e);
          return false;
        }
      }));
    }

    /**
     * Prepares all servers for a command.
     *
     * @param command The command to prepare.
     * @return true if all servers are ready for the command, false otherwise.
     */
    boolean allYes = true;
    for (Future<Boolean> future : futures) {
      try {
        allYes &= future.get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Exception during prepare phase", e);
        allYes = false;
      }
    }

    executor.shutdown();
    return allYes;
  }

  /**
   * Commits a command on all servers.
   *
   * @param command The command to commit.
   */
  private void commitAllServers(String command) {
    for (KeyValueRPC otherServer : otherServers) {
      try {
        otherServer.commit(command);
      } catch (RemoteException e) {
        logger.log(Level.SEVERE, "RemoteException while committing command: " + command, e);
      }
    }
  }

  /**
   * Aborts a command on all servers.
   *
   * @param command The command to abort.
   */
  private void abortAllServers(String command) {
    for (KeyValueRPC otherServer : otherServers) {
      try {
        otherServer.abort(command);
      } catch (RemoteException e) {
        logger.log(Level.SEVERE, "RemoteException while aborting command: " + command, e);
      }
    }
  }

  /**
   * Prepares this server for a command.
   *
   * @param command The command to prepare.
   * @return A string indicating whether this server is ready for the command.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public String prepare(String command) throws RemoteException {
    logger.info("Prepare request received: " + command);

    String[] parts = command.split(" ");
    String operation = parts[0];
    String key = parts[1];

    logger.info("Preparing to commit operation: " + operation + " on key: " + key);
    // Return "YES" to indicate ready to commit
    return "YES";
  }

  /**
   * Commits a command on this server.
   *
   * @param command The command to commit.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public void commit(String command) throws RemoteException {
    String[] parts = command.split(" ");
    if ("PUT".equals(parts[0])) {
      store.put(parts[1], parts[2]);
    } else if ("DELETE".equals(parts[0])) {
      store.remove(parts[1]);
    }
  }

  /**
   * Aborts a command on this server.
   *
   * @param command The command to abort.
   * @throws RemoteException If a remote communication error occurs.
   */
  @Override
  public void abort(String command) throws RemoteException {
    logger.info("Abort request received for command: " + command);
    String[] parts = command.split(" ");
    String operation = parts[0];
    String key = parts[1];

    logger.info("Aborting operation: " + operation + " on key: " + key);
  }

  /**
   * Coordinates the startup of this server and the other servers.
   *
   * @throws RemoteException If a remote communication error occurs.
   */
  private void coordinateStartup() throws RemoteException {
    logger.info("Master server coordinating startup");
    for (int i = 0; i < otherServerPorts.length; i++) {
      if (otherServerPorts[i] != port) {
        boolean serverReady = false;
        while (!serverReady) {
          try {
            Registry registry = LocateRegistry.getRegistry(otherServerAddresses[i], otherServerPorts[i]);
            KeyValueRPC server = (KeyValueRPC) registry.lookup("KeyValueRPC");
            server.signalReady();
            serverReady = true;
          } catch (Exception e) {
            logger.warning("Waiting for server on port " + otherServerPorts[i]);
            try {
              Thread.sleep(5000);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
    }
    logger.info("All servers are ready");
  }

  /**
   * Waits for a signal from the master server before starting.
   */
  private void waitForMasterSignal() {
    while (true) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void signalReady() throws RemoteException {
    // This method is called by the master to signal that this server can proceed
    logger.info("Received ready signal from master");
  }

  /**
   * The main method for the Server1 class.
   *
   * @param args Command line arguments, which should include the port, the addresses and ports of the other servers,
   *  whether this server is the master server, and the ID of this server.
   */
  public static void main(String args[]) {
    if (args.length != 6) {
      System.out.println(
          "Usage: java Server <port> <otherPort1> <otherPort2> <otherPort3> <otherPort4> <isMaster>");
      System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    String[] otherServerAddresses = new String[4];
    int[] otherServerPorts = new int[4];
    for (int i = 0; i < 4; i++) {
      otherServerAddresses[i] = "localhost";
      otherServerPorts[i] = Integer.parseInt(args[i + 1]);
    }
    boolean isMaster = Boolean.parseBoolean(args[5]);

    try {
      Server1 server = new Server1(port, otherServerAddresses, otherServerPorts, isMaster, Arrays.asList(args).indexOf(String.valueOf(port)));
      Registry registry = LocateRegistry.createRegistry(port);
      registry.bind("KeyValueRPC", server);
      logger.info("Server bound to registry on port " + port);

      if (isMaster) {
        server.coordinateStartup();
      } else {
        server.waitForMasterSignal();
      }

      server.connectToOtherServers();
      logger.info("Server fully started and connected on port " + port);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Server exception", e);
      e.printStackTrace();
    }
  }
}