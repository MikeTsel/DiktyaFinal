package server;

import model.Notification;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Social Network Server implementation.
 * This server provides functionality for a social network application
 * including client management, file synchronization, and search capabilities.
 */
public class SocialNetworkServer {
    private static final int MAX_THREADS = 8;

    private static final String SRC_FOLDER = "src";
    private static final String DATA_FOLDER = SRC_FOLDER + File.separator + "data";
    private static final String SOCIAL_GRAPH_FILENAME = "SocialGraph.txt";
    private Map<String, List<Notification>> clientNotifications;
    // Permissions for photo access: key=ownerID:filename, value=set of requester IDs
    private Map<String, Set<String>> photoPermissions;
    private static final Logger logger = Logger.getLogger(SocialNetworkServer.class.getName());

    private int port;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean running;

    // Catalog of connected clients with their network information
    private Map<String, ClientInfo> clientCatalog;

    // Lock for thread-safe access to the client catalog
    private final Object catalogLock = new Object();

    /**
     * Constructor for the Social Network Server
     * @param port The port number on which the server will listen
     */
    public SocialNetworkServer(int port) {
        this.port = port;
        this.clientCatalog = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        this.running = false;
        this.clientNotifications = new ConcurrentHashMap<>();
        this.photoPermissions = new ConcurrentHashMap<>();



    }

    /**
     * Initialize the basic folder structure needed for the server
     */
    private void initializeFolderStructure() {
        try {
            Path dataDir = Paths.get(DATA_FOLDER);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                logger.info("Created data directory: " + dataDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.severe("Error initializing folder structure: " + e.getMessage());
        }
    }

    public void addNotification(Notification notification) {
        String receiverID = notification.getReceiverID();

        logger.info("Adding notification from " + notification.getSenderID() +
                " to " + receiverID + " of type " + notification.getType() +
                ": " + notification.getContent());

        synchronized (clientNotifications) {
            // Create a list for this client if it doesn't exist
            if (!clientNotifications.containsKey(receiverID)) {
                clientNotifications.put(receiverID, new ArrayList<>());
                logger.info("Created new notification list for client " + receiverID);
            }

            // Add the notification to the client's list
            clientNotifications.get(receiverID).add(notification);

            logger.info("Client " + receiverID + " now has " +
                    clientNotifications.get(receiverID).size() + " notifications");
        }

        logger.info("Added notification for client " + receiverID + ": " + notification.toString());
    }
    public List<Notification> getClientNotifications(String clientID) {
        synchronized (clientNotifications) {
            if (!clientNotifications.containsKey(clientID)) {
                return new ArrayList<>();
            }

            // Create a new list to hold unread and pending follow request notifications
            List<Notification> activeNotifications = new ArrayList<>();

            for (Notification notification : clientNotifications.get(clientID)) {
                // Include if:
                // 1. Notification is unread, or
                // 2. It's a pending follow request
                if (!notification.isRead() ||
                        (notification.getType().equals("follow_request") &&
                                notification.getStatus().equals("pending"))) {
                    activeNotifications.add(notification);
                }
            }

            return activeNotifications;
        }
    }

    public List<Notification> getPendingFollowRequests(String clientID) {
        List<Notification> pendingRequests = new ArrayList<>();

        synchronized (clientNotifications) {
            if (!clientNotifications.containsKey(clientID)) {
                return pendingRequests;
            }

            for (Notification notification : clientNotifications.get(clientID)) {
                if (notification.getType().equals("follow_request") &&
                        notification.getStatus().equals("pending")) {
                    pendingRequests.add(notification);
                }
            }
        }

        return pendingRequests;
    }
    public void updateFollowRequestStatus(String senderID, String receiverID, String status) {
        synchronized (clientNotifications) {
            if (!clientNotifications.containsKey(receiverID)) {
                return;
            }

            for (Notification notification : clientNotifications.get(receiverID)) {
                if (notification.getType().equals("follow_request") &&
                        notification.getSenderID().equals(senderID) &&
                        notification.getStatus().equals("pending")) {

                    notification.setStatus(status);
                    logger.info("Updated follow request status from " + senderID +
                            " to " + receiverID + ": " + status);
                    return;
                }
            }
        }

    }


    /**
     * Initialize social graph file
     * Creates the social graph file if it doesn't exist
     */
    private void initializeSocialGraphFile() {
        try {
            // Log the working directory
            String workingDir = System.getProperty("user.dir");

            // Create the social graph file if it doesn't exist
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
            if (!Files.exists(socialGraphPath)) {
                Files.createFile(socialGraphPath);
                logger.info("Created social graph file: " + socialGraphPath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.severe("Error initializing social graph file: " + e.getMessage());
        }
    }

    /**
     * Starts the server and begins accepting client connections
     */
    public void start() {
        try {

            initializeFolderStructure();

            initializeSocialGraphFile();

            // Bind to all network interfaces (0.0.0.0) instead of just localhost
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            logger.info("Server started on port " + port + " and is accessible from all network interfaces");

            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleNewClient(clientSocket);
                } catch (IOException e) {
                    if (running) {
                        logger.severe("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Error starting server: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * Handles a new client connection by creating a new client handler thread
     * @param clientSocket The socket connected to the client
     */
    private void handleNewClient(Socket clientSocket) {
        // Check if we have reached the maximum number of clients
        if (clientCatalog.size() >= MAX_THREADS) {
            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("Server is at maximum capacity. Please try again later.");
                clientSocket.close();
                logger.warning("Rejected client connection due to maximum capacity");
            } catch (IOException e) {
                logger.severe("Error rejecting client: " + e.getMessage());
            }
            return;
        }

        // Create and submit a new client handler to the thread pool
        threadPool.submit(new ClientHandler(clientSocket, this));
        logger.info("New client connection accepted from " +
                clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
    }

    /**
     * Stops the server and releases resources
     */
    public void shutdown() {
        running = false;

        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.severe("Error closing server socket: " + e.getMessage());
            }
        }

        // Shutdown the thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }

        logger.info("Server has been shut down");
    }

    /**
     * Updates the client catalog with new client information
     * @param clientID The ID of the client
     * @param ipAddress The IP address of the client
     * @param port The port number of the client
     */
    void updateClientCatalog(String clientID, InetAddress ipAddress, int port) {
        synchronized (catalogLock) {
            clientCatalog.put(clientID, new ClientInfo(clientID, ipAddress, port));
            logger.info("Updated client catalog: Client " + clientID + " added");
        }
    }

    /**
     * Removes a client from the catalog
     * @param clientID The ID of the client to remove
     */
    void removeClientFromCatalog(String clientID) {
        synchronized (catalogLock) {
            clientCatalog.remove(clientID);
            logger.info("Updated client catalog: Client " + clientID + " removed");
        }
    }

    /**
     * Gets a copy of the client catalog
     * @return A copy of the client catalog
     * Not Used for now
     */
    Map<String, ClientInfo> getClientCatalog() {
        synchronized (catalogLock) {
            return new HashMap<>(clientCatalog);
        }
    }

    // --- Photo access permission management ---
    void grantPhotoAccess(String ownerID, String requesterID, String fileName) {
        String key = ownerID + ":" + fileName;
        photoPermissions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(requesterID);
    }

    boolean hasPhotoAccess(String ownerID, String requesterID, String fileName) {
        String key = ownerID + ":" + fileName;
        Set<String> allowed = photoPermissions.get(key);
        return allowed != null && allowed.contains(requesterID);
    }

    void revokePhotoAccess(String ownerID, String requesterID, String fileName) {
        String key = ownerID + ":" + fileName;
        Set<String> allowed = photoPermissions.get(key);
        if (allowed != null) {
            allowed.remove(requesterID);
            if (allowed.isEmpty()) {
                photoPermissions.remove(key);
            }
        }
    }


    public static void main(String[] args) {
        LoggingConfig.configureLogging();

        int port = 8000; // Default port

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8000.");
            }
        }

        SocialNetworkServer server = new SocialNetworkServer(port);
        server.start();
    }
}