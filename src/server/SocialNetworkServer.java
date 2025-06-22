package server;
import model.Notification;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
// Main server managing clients and requests.
public class SocialNetworkServer {
    private static final int MAX_THREADS = 8;
    private static final String SRC_FOLDER = "src";
    private static final String DATA_FOLDER = SRC_FOLDER + File.separator + "data";
    private static final String SOCIAL_GRAPH_FILENAME = "SocialGraph.txt";
    private Map<String, List<Notification>> clientNotifications;
    private Map<String, Set<String>> photoPermissions;
    private static final Logger logger = Logger.getLogger(SocialNetworkServer.class.getName());
    private int port;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean running;
    private Map<String, ClientInfo> clientCatalog;
    private final Object catalogLock = new Object();
    public SocialNetworkServer(int port) {
        this.port = port;
        this.clientCatalog = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        this.running = false;
        this.clientNotifications = new ConcurrentHashMap<>();
        this.photoPermissions = new ConcurrentHashMap<>();
    }
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
            if (!clientNotifications.containsKey(receiverID)) {
                clientNotifications.put(receiverID, new ArrayList<>());
                logger.info("Created new notification list for client " + receiverID);
            }
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
            List<Notification> activeNotifications = new ArrayList<>();
            for (Notification notification : clientNotifications.get(clientID)) {
                if (!notification.isRead() ||
                        ((notification.getType().equals("follow_request") ||
                                notification.getType().equals("photo_request")) &&
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
    public List<Notification> getPendingPhotoRequests(String clientID) {
        List<Notification> pendingRequests = new ArrayList<>();
        synchronized (clientNotifications) {
            if (!clientNotifications.containsKey(clientID)) {
                return pendingRequests;
            }
            for (Notification notification : clientNotifications.get(clientID)) {
                if (notification.getType().equals("photo_request") &&
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
    public void updatePhotoRequestStatus(String senderID, String receiverID, String fileName, String status) {
        synchronized (clientNotifications) {
            if (!clientNotifications.containsKey(receiverID)) {
                return;
            }
            for (Notification notification : clientNotifications.get(receiverID)) {
                if (notification.getType().equals("photo_request") &&
                        notification.getSenderID().equals(senderID) &&
                        notification.getContent().contains(fileName) &&
                        notification.getStatus().equals("pending")) {
                    notification.setStatus(status);
                    return;
                }
            }
        }
    }
    private void initializeSocialGraphFile() {
        try {
            String workingDir = System.getProperty("user.dir");
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
            if (!Files.exists(socialGraphPath)) {
                Files.createFile(socialGraphPath);
                logger.info("Created social graph file: " + socialGraphPath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.severe("Error initializing social graph file: " + e.getMessage());
        }
    }
    public void start() {
        try {
            initializeFolderStructure();
            initializeSocialGraphFile();
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            logger.info("Server started on port " + port + " and is accessible from all network interfaces");
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
    private void handleNewClient(Socket clientSocket) {
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
        threadPool.submit(new ClientHandler(clientSocket, this));
        logger.info("New client connection accepted from " +
                clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
    }
    public void shutdown() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.severe("Error closing server socket: " + e.getMessage());
            }
        }
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
    void updateClientCatalog(String clientID, InetAddress ipAddress, int port) {
        synchronized (catalogLock) {
            clientCatalog.put(clientID, new ClientInfo(clientID, ipAddress, port));
            logger.info("Updated client catalog: Client " + clientID + " added");
        }
    }
    void removeClientFromCatalog(String clientID) {
        synchronized (catalogLock) {
            clientCatalog.remove(clientID);
            logger.info("Updated client catalog: Client " + clientID + " removed");
        }
    }
    Map<String, ClientInfo> getClientCatalog() {
        synchronized (catalogLock) {
            return new HashMap<>(clientCatalog);
        }
    }
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
        int port = 8000; 
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