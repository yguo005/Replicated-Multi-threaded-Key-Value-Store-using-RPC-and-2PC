# CS 6650 Project #3: Replicated Multi-threaded Key-Value Store using RPC and 2PC

## Project Overview

This project extends Project #2, further developing the multi-threaded Key-Value store. The key enhancements are:

1.  **Server Replication:** The Key-Value Store server will be replicated across 5 distinct server instances. This is aimed at increasing server bandwidth and ensuring availability. Clients should be able to contact any of these five replicas for any operation (PUT, GET, DELETE). GET operations should return consistent data regardless of which replica is contacted.
2.  **Two-Phase Commit (2PC) for Consistency:** To ensure data consistency across all 5 replicas for PUT and DELETE operations, a two-phase commit protocol must be implemented. When a client issues a PUT or DELETE to any server replica (acting as the coordinator for that request), that replica will manage the 2PC protocol with the other four replicas.
    *   **Phase 1 (Prepare/Vote):** The coordinator sends a prepare/request message to all other replicas. Replicas respond with an ACK (agreement to commit).
    *   **Phase 2 (Commit/Go):** If all replicas ACK, the coordinator sends a commit/Go message. Replicas perform the operation and ACK the commit.
    *   The project assumes no server failures, so 2PC will not stall. Defensive coding with timeouts for 2PC messages is suggested but not strictly required for the "no-fail" assumption.

The client's role in pre-populating the store and performing a set number of operations (5 PUTs, 5 GETs, 5 DELETEs) remains the same. The implementation is expected to be in Java, well-factored, and well-commented.

## Features to Implement

*   **RPC-based Communication:** (Leveraged from Project #2)
    *   Client-server interaction using an RPC framework (e.g., Java RMI, Apache Thrift).
*   **Multi-threaded Server:** (Leveraged from Project #2)
    *   Each of the 5 server replicas must be multi-threaded to handle concurrent client requests and internal 2PC messages.
*   **Server Replication:**
    *   Deploy and manage 5 instances of the Key-Value Store server.
    *   Clients can direct requests to any of the 5 server instances.
*   **Two-Phase Commit (2PC) for PUT/DELETE Operations:**
    *   When a server replica receives a PUT or DELETE request from a client, it acts as the **coordinator** for that specific transaction.
    *   **Phase 1 (Prepare/Vote):**
        *   Coordinator sends a "prepare" (or "can you commit?") message for the PUT/DELETE operation to all other 4 replicas. This message should include the key and (for PUT) the value.
        *   Each participant replica, upon receiving the prepare message, prepares to make the change (e.g., logs it, locks the key) and responds with an `ACK_PREPARE` (or "YES_VOTE") to the coordinator.
    *   **Phase 2 (Commit/Go):**
        *   If the coordinator receives `ACK_PREPARE` from all participant replicas:
            *   The coordinator commits the change to its local Key-Value store.
            *   The coordinator sends a "commit" (or "GO") message to all participant replicas.
        *   Each participant replica, upon receiving the "commit" message, applies the change to its local Key-Value store and sends an `ACK_COMMIT` back to the coordinator.
        *   The coordinator, after receiving all `ACK_COMMIT` messages (or after its own commit if it's the only one involved in a simplified 1-server scenario, though not the case here), responds with success to the original client.
    *   *Note: The project simplifies by assuming no server failures, so abort/rollback logic for 2PC is not a primary focus, but handling ACKs is crucial.*
*   **Data Consistency for GET Operations:**
    *   A GET request to any replica should return the latest committed value for the key. Since 2PC ensures all replicas are consistent after a PUT/DELETE, a GET can be served directly from the contacted replica's local store.
*   **Client Functionality:**
    *   Ability to connect to any of the 5 server replicas.
    *   Pre-populate the replicated Key-Value store.
    *   Perform at least 5 PUTs, 5 GETs, and 5 DELETEs after pre-population.

## Suggested Technologies (for Java)

*   **RPC Framework:** Java RMI or Apache Thrift (as in Project #2).
*   **Multi-threading:** Java's `java.util.concurrent` package and synchronization primitives.
*   **Inter-Server Communication (for 2PC):** The same RPC mechanism used for client-server communication should be used for communication between server replicas during the 2PC protocol. Each server will act as both a server (to clients and other servers) and a client (to other servers during 2PC).
*   **Data Store:** `java.util.concurrent.ConcurrentHashMap` or a `HashMap` with appropriate external synchronization.

## Project Structure (Conceptual)

*   **`KeyValueStoreInterface.java`**: Remote interface for client-server operations (PUT, GET, DELETE).
*   **`TwoPhaseCommitInterface.java` (or similar)**: A separate remote interface for 2PC operations between servers (e.g., `prepare(operation, key, value)`, `commit(operation, key)`, `ackPrepare()`, `ackCommit()`).
*   **`KeyValueServer.java`**:
    *   Implements `KeyValueStoreInterface` for client requests.
    *   Implements `TwoPhaseCommitInterface` (or acts as a client to it) for participating in 2PC.
    *   Manages its local Key-Value store.
    *   Handles multi-threading for client requests and 2PC messages.
    *   Contains logic to act as a coordinator or participant in 2PC.
    *   Knows the addresses/ports of the other 4 server replicas.
*   **`KeyValueClient.java`**:
    *   Connects to one of the 5 server replicas (e.g., chosen randomly, or via configuration).
    *   Invokes methods on `KeyValueStoreInterface`.

## Compilation and Running

Instructions will be highly dependent on the chosen RPC framework.

1.  **Define Service Interfaces:** For client-server interaction and for inter-server 2PC communication.
2.  **Generate Stubs/Skeletons:** If using a framework like Thrift.
3.  **Implement Server Logic:**
    *   Implement both sets of interface methods.
    *   Set up RPC mechanisms for both client-facing and server-facing (2PC) services.
    *   Ensure each server instance can locate and communicate with the other four.
4.  **Implement Client Logic:**
    *   Configure client to connect to any of the 5 server addresses.
5.  **Compile:** Compile all source code, including any generated RPC files and necessary libraries.
6.  **Run the Servers:**
    *   Start 5 instances of your `KeyValueServer` application. Each will need its own port for client communication and possibly a distinct identifier or port for 2PC RMI/Thrift services if not shared. Ensure each server knows how to reach the other replicas.
    *   Example (very conceptual, assuming each server listens on a different port and configuration provides peer info):
        ```bash
        java KeyValueServer --id 1 --port 8001 --peer-ports 8002,8003,8004,8005
        java KeyValueServer --id 2 --port 8002 --peer-ports 8001,8003,8004,8005
        ...and so on for 5 servers
        ```
7.  **Run the Client(s):**
    *   The client should be able to connect to any of the 5 server ports.
    *   Example: `java KeyValueClient server1_host 8001`
    *   Multiple clients should be able to run concurrently.

## Key Considerations

*   **2PC Logic:** Carefully implement the prepare and commit phases, including sending messages to all replicas and waiting for ACKs.
*   **Coordinator Role:** The server replica that initially receives the client's PUT/DELETE request acts as the coordinator for that transaction's 2PC.
*   **Participant Role:** All other replicas (and the coordinator for its own data) act as participants.
*   **Addressing Replicas:** Each server needs to know the network addresses (host/port for their RPC service) of all other replicas to participate in 2PC. This could be hardcoded, passed as command-line arguments, or read from a configuration file.
*   **Concurrency and Synchronization:** The Key-Value store on each server replica must be protected against concurrent access from multiple client threads and from 2PC operation threads.



