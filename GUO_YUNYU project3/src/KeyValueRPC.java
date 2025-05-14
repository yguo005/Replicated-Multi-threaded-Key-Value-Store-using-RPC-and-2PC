import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The KeyValueRPC interface defines the methods that a key-value store
 * should implement for remote method invocation
 */

public interface KeyValueRPC extends Remote {
  /**
   * Stores a key-value pair in the key-value store.
   *
   * @param key The key to store.
   * @param value The value to associate with the key.
   * @return A string indicating the result of the operation.
   * @throws RemoteException If a remote communication error occurs.
   */
  String put(String key, String value) throws RemoteException;

  /**
   * Retrieves the value associated with a key from the key-value store.
   *
   * @param key The key to retrieve the value for.
   * @return The value associated with the key.
   * @throws RemoteException If a remote communication error occurs.
   */
  String get(String key) throws RemoteException;

  /**
   * Deletes a key-value pair from the key-value store.
   *
   * @param key The key to delete.
   * @return A string indicating the result of the operation.
   * @throws RemoteException If a remote communication error occurs.
   */
  String delete(String key) throws RemoteException;

  /**
   * Retrieves all key-value pairs from the key-value store.
   *
   * @return A string representation of all key-value pairs.
   * @throws RemoteException If a remote communication error occurs.
   */
  String getAll() throws RemoteException;

  /**
   * Prepares a command for execution in the key-value store.
   *
   * @param command The command to prepare.
   * @return A string indicating whether the command is ready for execution.
   * @throws RemoteException If a remote communication error occurs.
   */
  String prepare(String command) throws RemoteException;

  /**
   * Commits a command in the key-value store.
   *
   * @param command The command to commit.
   * @throws RemoteException If a remote communication error occurs.
   */
  void commit(String command) throws RemoteException;

  /**
   * Aborts a command in the key-value store.
   *
   * @param command The command to abort.
   * @throws RemoteException If a remote communication error occurs.
   */
  void abort(String command) throws RemoteException;

  /**
   * Signals that the key-value store is ready for commands.
   *
   * @throws RemoteException If a remote communication error occurs.
   */
  void signalReady() throws RemoteException;
}