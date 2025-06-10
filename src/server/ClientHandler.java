package server;

import model.Notification;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handler for client connections. Each client will be serviced by a separate thread.
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    static {
        // Ensure this logger inherits from the root logger
        logger.setUseParentHandlers(true);
    }
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientID;
    private SocialNetworkServer server;
    private FileManager fileManager;
    private boolean authenticated;
    private String downloadFileName;
    private String downloadSourceClientID;
    private String downloadSequenceNumber;
    // Client's preferred language for photo descriptions ("en" or "gr")
    private String languagePreference = "en";

    /**
     * Constructor for ClientHandler
     * @param socket The socket connected to the client
     * @param server The server instance that created this handler
     */
    public ClientHandler(Socket socket, SocialNetworkServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.fileManager = new FileManager();
        this.authenticated = false;
    }

    @Override
    public void run() {
        try {
            // Set up input and output streams
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Process client commands (starting with login/signup)
            processClientCommands();

        } catch (IOException e) {
            logger.severe("Error handling client connection: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    /**
     * Processes commands from the client
     */
    private void processClientCommands() throws IOException {
        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            if (inputLine.equals("exit")) {
                break;
            }

            // Parse command structure (command:parameters)
            String[] parts = inputLine.split(":", 2);
            if (parts.length != 2) {
                out.println("Error: Invalid command format");
                continue;
            }

            String command = parts[0].trim();
            String parameters = parts[1].trim();

            // Handle authentication commands
            if (command.equals("login") || command.equals("signup")) {
                handleAuthentication(command, parameters);
                continue;
            }

            // Check if the client is authenticated
            if (!authenticated) {
                out.println("Error: Please login or signup first");
                continue;
            }

            // Process other commands
            switch (command) {
                case "post":
                    handlePost(parameters);
                    break;
                case "reply":
                    handleReply(parameters);
                    break;
                case "follow":
                    handleFollow(parameters);
                    break;
                case "unfollow":
                    handleUnfollow(parameters);
                    break;
                case "upload":
                    handleUpload(parameters);
                    break;
                case "access_profile":
                    handleAccessProfile(parameters);
                    break;
                case "search":
                    handleSearch(parameters);
                    break;
                case "sync":
                    handleSync(parameters);
                    break;
                case "download":
                    handleDownload(parameters);
                    break;
                case "follow_request":
                    handleFollowRequest(parameters);
                    break;
                case "repost":
                    handleRepost(parameters);
                    break;
                case "set_language":
                    handleSetLanguage(parameters);
                    break;

                case "get_notifications":
                    handleGetNotifications();
                    break;
                case "follow_response":
                    handleFollowResponse(parameters);
                    break;

                case "download_syn":
                    handleDownloadSyn(parameters);
                    break;
                case "download_ack":
                    handleDownloadAck(parameters);
                    break;
                case "ask_comment":
                    handleAskComment(parameters);
                    break;
                case "approve_comment":
                    handleApproveComment(parameters);
                    break;
                case "comment":
                    handleComment(parameters);
                    break;
                default:
                    out.println("Error: Unknown command");
            }
        }
    }

    /**
     * Handles authentication (login or signup)
     * @param command The authentication command (login or signup)
     * @param clientID The client ID
     */
    private void handleAuthentication(String command, String clientID) {
        this.clientID = clientID;

        if (command.equals("login")) {
            // Check if the client exists
            if (fileManager.clientExists(clientID)) {
                // Update the client catalog
                server.updateClientCatalog(clientID, clientSocket.getInetAddress(), clientSocket.getPort());

                authenticated = true;
                loadLanguagePreference();
                out.println("Welcome back, client " + clientID);
                logger.info("Client " + clientID + " logged in from " +
                        clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
            } else {
                out.println("Error: Client does not exist. Please signup first.");
            }
        } else if (command.equals("signup")) {
            // Check if the client already exists
            if (fileManager.clientExists(clientID)) {
                out.println("Error: Client ID already exists. Please choose another one or login.");
            } else {
                // Initialize client files and directories
                if (fileManager.initializeClientFiles(clientID)) {
                    // Update the client catalog
                    server.updateClientCatalog(clientID, clientSocket.getInetAddress(), clientSocket.getPort());

                    authenticated = true;
                    languagePreference = "en";
                    saveLanguagePreference();
                    out.println("Welcome client " + clientID);
                    logger.info("New client " + clientID + " signed up and connected from " +
                            clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                } else {
                    out.println("Error: Failed to create client account");
                }
            }
        }
    }
    private void handleSync(String clientID) {
        // Verify the client ID
        if (!this.clientID.equals(clientID)) {
            out.println("Error: Client ID mismatch");
            return;
        }

        // Call the synchronizer
        boolean syncResult = ClientServerSynchronizer.synchronizeClientData(clientID);

        if (syncResult) {
            out.println("Data synchronized successfully");
        } else {
            out.println("Synchronization failed or was incomplete");
        }
    }
    /**
     * Handles a search command from the client
     * @param fileName The name of the photo file to search for
     */

    private void handleSearch(String parameters) {
        try {
            if (parameters == null || parameters.trim().isEmpty()) {
                out.println("ERROR:Please provide a valid file name to search for");
                return;
            }

            String[] parts = parameters.split(":", 2);
            String fileName = parts[0].trim();
            String lang = null;
            if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                lang = parts[1].trim().toLowerCase();
                if (!lang.equals("en") && !lang.equals("gr")) {
                    out.println("ERROR:Invalid language. Use 'en' or 'gr'");
                    return;
                }
            }

            logger.info("Client " + clientID + " is searching for photo: " + fileName +
                    (lang != null ? " with language " + lang : ""));

            // Get the users that the client follows from the social graph
            List<String> following = new ArrayList<>();

            // Read the social graph file to find who the client follows
            Path socialGraphPath = Paths.get(FileManager.DATA_FOLDER, FileManager.SOCIAL_GRAPH_FILENAME);
            if (Files.exists(socialGraphPath)) {
                List<String> graphLines = Files.readAllLines(socialGraphPath);

                for (String line : graphLines) {
                    String[] lineParts = line.split("\\s+");
                    // If this line defines followers of a user
                    if (lineParts.length > 0) {
                        String lineClientID = lineParts[0];

                        // Check if current client is a follower of this user
                        for (int i = 1; i < lineParts.length; i++) {
                            if (lineParts[i].equals(clientID)) {
                                // Client follows this user
                                following.add(lineClientID);
                                break;
                            }
                        }
                    }
                }
            }

            if (following.isEmpty()) {
                out.println("RESULT:You are not following any users. No search results.");
                logger.info("Client " + clientID + " is not following anyone. No search results.");
                return;
            }

            logger.info("Client " + clientID + " is following: " + String.join(", ", following));

            // Now search for the file in the photos directories of users the client follows
            List<String> results = new ArrayList<>();

            for (String followedUser : following) {
                Path photoPath = Paths.get(FileManager.DATA_FOLDER, followedUser, "photos", fileName);

                if (!Files.exists(photoPath)) {
                    continue;
                }

                if (lang != null) {
                    String baseName = fileName.contains(".") ?
                            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                    Path descPath = Paths.get(FileManager.DATA_FOLDER, followedUser,
                            "photos", baseName + "_" + lang + ".txt");
                    if (!Files.exists(descPath)) {
                        continue;
                    }
                }

                results.add(followedUser);
                logger.info("Found matching photo at: " + photoPath);
            }

            if (results.isEmpty()) {
                out.println("RESULT:No matching photos found in your social graph.");
                logger.info("No matching photos found for client " + clientID);
            } else {
                StringBuilder resultBuilder = new StringBuilder("RESULT:");
                resultBuilder.append(results.size()).append(" result(s) found:");

                // Add an explicit separator that won't be confused with newlines
                resultBuilder.append("##ENTRIES##");

                for (int i = 0; i < results.size(); i++) {
                    resultBuilder.append(i + 1).append(". Client ID: ").append(results.get(i))
                            .append(" - File: ").append(fileName);

                    if (i < results.size() - 1) {
                        resultBuilder.append("##NEWLINE##");
                    }
                }

                String resultString = resultBuilder.toString();
                logger.info("Search results for client " + clientID + ": [" + resultString + "]");
                out.println(resultString);
            }
        } catch (IOException e) {
            logger.severe("Error searching for photo: " + e.getMessage());
            out.println("ERROR:Failed to search for photo: " + e.getMessage());
        }
    }
    /**
     * Processes the SYN message of the 3-way handshake
     * @param clientID The ID of the client initiating the handshake
     */
    private void handleDownloadSyn(String clientID) {
        try {
            logger.info("Received DOWNLOAD_SYN from client " + this.clientID);

            // Verify that the clientID matches the authenticated client
            if (!this.clientID.equals(clientID)) {
                logger.warning("Client ID mismatch in handshake. Expected: " + this.clientID + ", received: " + clientID);
                out.println("ERROR:Client ID mismatch");
                return;
            }

            // Generate a sequence number for this handshake
            // For simplicity, we'll use a timestamp
            String sequenceNumber = String.valueOf(System.currentTimeMillis());

            // Store the sequence number for verification in the ACK phase
            this.downloadSequenceNumber = sequenceNumber;

            logger.info("Handshake Step 1/3: Client " + this.clientID + " initiated 3-way handshake (SYN)");

            // Send SYN-ACK response with sequence number
            out.println("SYN_ACK:" + sequenceNumber);
            logger.info("Handshake Step 2/3: Sent SYN-ACK to client " + clientID + " with sequence number " + sequenceNumber);
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download SYN: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processes the ACK message of the 3-way handshake
     * @param parameters Parameters containing sequence number and file information
     */
    private void handleDownloadAck(String parameters) {
        try {
            logger.info("Received DOWNLOAD_ACK from client " + this.clientID + " with parameters: " + parameters);

            // Parse parameters: sequenceNumber:fileName:sourceClientID
            String[] parts = parameters.split(":", 3);
            if (parts.length != 3) {
                logger.warning("Invalid ACK parameters: " + parameters);
                out.println("ERROR:Invalid ACK parameters");
                return;
            }

            String receivedSequence = parts[0];
            String fileName = parts[1];
            String sourceClientID = parts[2];

            logger.info("Handshake Step 3/3: Client " + this.clientID + " sent ACK with sequence " +
                    receivedSequence + " for file " + fileName + " from client " + sourceClientID);

            // Verify the sequence number
            if (!receivedSequence.equals(this.downloadSequenceNumber)) {
                logger.warning("Sequence number mismatch. Expected: " + this.downloadSequenceNumber +
                        ", received: " + receivedSequence);
                out.println("ERROR:Sequence number mismatch");
                return;
            }

            // Verify that the fileName and sourceClientID match what was previously requested
            if (!fileName.equals(this.downloadFileName) || !sourceClientID.equals(this.downloadSourceClientID)) {
                logger.warning("File or source client mismatch. Expected: " + this.downloadFileName +
                        " from " + this.downloadSourceClientID + ", received: " + fileName +
                        " from " + sourceClientID);
                out.println("ERROR:File or source client mismatch");
                return;
            }

            // Handshake completed successfully
            logger.info("3-way handshake completed successfully for download of " + fileName +
                    " from client " + sourceClientID);
            out.println("TRANSFER_READY");

            // Begin file transfer
            transferFile(fileName, sourceClientID);
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download ACK: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles a file download command from the client
     * @param parameters The parameters for the download (fileName:sourceClientID)
     */
    private void handleDownload(String parameters) {
        try {
            logger.info("Received DOWNLOAD request from client " + clientID + " with parameters: " + parameters);

            // Parse parameters
            String[] parts = parameters.split(":", 2);
            if (parts.length != 2) {
                logger.warning("Invalid download parameters: " + parameters);
                out.println("ERROR:Invalid parameters format. Expected 'fileName:sourceClientID'");
                return;
            }

            String fileName = parts[0].trim();
            String sourceClientID = parts[1].trim();

            logger.info("Client " + clientID + " requested to download " + fileName + " from client " + sourceClientID);

            // Check if the source client exists
            if (!fileManager.clientExists(sourceClientID)) {
                logger.warning("Source client " + sourceClientID + " does not exist");
                out.println("ERROR:Source client " + sourceClientID + " does not exist");
                return;
            }

            // Check if the requesting client is following the source client
            boolean isFollowing = fileManager.isFollowing(clientID, sourceClientID);
            if (!isFollowing) {
                logger.warning("Client " + clientID + " is not following client " + sourceClientID);
                out.println("ERROR:You are not following client " + sourceClientID);
                return;
            }

            // Check if the file exists in the source client's directory
            Path photoPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", fileName);
            if (!Files.exists(photoPath)) {
                logger.warning("File " + fileName + " not found in client " + sourceClientID + "'s directory");
                out.println("ERROR:File " + fileName + " not found in client " + sourceClientID + "'s directory");
                return;
            }

            // Store the download information for use during the handshake
            this.downloadFileName = fileName;
            this.downloadSourceClientID = sourceClientID;

            // Initiate the 3-way handshake
            logger.info("Preparing to initiate 3-way handshake for download of " + fileName +
                    " from client " + sourceClientID);
            out.println("HANDSHAKE_INIT");
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Handles a post command from the client
     * @param content The content of the post
     */
    private void handlePost(String content) {
        try {
            // Make sure the client directory exists
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }

            // Get the profile file path
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);

            // Create the file if it doesn't exist
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }

            // Add timestamp to the post
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedPost = "[" + timestamp + "] " + content;

            // Append the post to the profile file
            Files.write(profilePath, (formattedPost + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);

            // Log the post creation
            logger.info("Client " + clientID + " added post to profile: " + content);

            // Return success message with the formatted post so client knows what was added
            out.println("Post created successfully! Your profile has been updated with: " + formattedPost);

            logger.info("About to notify followers for upload: " + formattedPost);
            notifyFollowersAboutPost(formattedPost);
        } catch (IOException e) {
            out.println("Error creating post: " + e.getMessage());
            logger.severe("Error updating profile for client " + clientID + ": " + e.getMessage());
        }
    }
    private void handleFollowRequest(String targetID) {
        // Check if the target client exists
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }

        // Create a notification for the target client
        Notification notification = new Notification(
                clientID,
                targetID,
                "follow_request",
                "wants to follow you."
        );

        // Add the notification to the server
        server.addNotification(notification);

        out.println("Follow request sent to client " + targetID + ". Waiting for their response.");
        logger.info("Client " + clientID + " sent a follow request to client " + targetID);
    }
    private void handleAccessProfile(String targetID) {
        // Check if the target client exists
        if (!fileManager.clientExists(targetID)) {
            out.println("ERROR:Client " + targetID + " does not exist.");
            logger.warning("Client " + clientID + " attempted to access non-existent client " + targetID);
            return;
        }

        // Check if the requesting client is following the target client
        boolean isFollowing = fileManager.isFollowing(clientID, targetID);
        if (!isFollowing) {
            // Client is not following the target, deny access
            handleDenyProfile(targetID);
            return;
        }

        try {
            // Get the profile file path
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, targetID, "Profile_42" + targetID);

            // Check if the profile file exists
            if (!Files.exists(profilePath)) {
                out.println("ERROR:Profile for client " + targetID + " not found.");
                logger.warning("Profile for client " + targetID + " not found when requested by " + clientID);
                return;
            }

            // Read the profile content
            List<String> profileContent = Files.readAllLines(profilePath);

            // Build the response
            StringBuilder response = new StringBuilder();
            response.append("PROFILE_START");

            for (String line : profileContent) {
                response.append("\n").append(line);
            }

            response.append("\nPROFILE_END");

            // Send the profile content to the client
            out.println(response.toString());

            logger.info("Client " + clientID + " accessed profile of client " + targetID);

            // Create a notification for the profile owner that someone viewed their profile
            Notification notification = new Notification(
                    clientID,
                    targetID,
                    "system",
                    clientID + " viewed your profile."
            );
            server.addNotification(notification);

        } catch (IOException e) {
            out.println("ERROR:Failed to read profile: " + e.getMessage());
            logger.severe("Error reading profile for client " + targetID + ": " + e.getMessage());
        }
    }

    private void handleDenyProfile(String targetID) {
        out.println("DENIED:You do not have permission to access the profile of client " + targetID + ". You must follow them first.");
        logger.warning("Client " + clientID + " was denied access to profile of client " + targetID + " (not following)");

        // Create a notification for the target client
        Notification notification = new Notification(
                clientID,
                targetID,
                "system",
                clientID + " attempted to view your profile but was denied (not following you)."
        );
        server.addNotification(notification);
    }
    /**
     * Handles a request to get all notifications for the client
     */
    private void handleGetNotifications() {
        List<Notification> notifications = server.getClientNotifications(clientID);

        if (notifications.isEmpty()) {
            out.println("No notifications.");
            return;
        }

        // First, send just the first notification
        out.println(notifications.get(0).toString());

        // Wait for the client to request more data
        try {
            String clientResponse = in.readLine();
            if (clientResponse.equals("continue_reading")) {
                // Send the rest of the notifications
                for (int i = 1; i < notifications.size(); i++) {
                    out.println(notifications.get(i).toString());

                    // Mark as read (except pending follow requests)
                    if (!notifications.get(i).isRead() &&
                            !(notifications.get(i).getType().equals("follow_request") &&
                                    notifications.get(i).getStatus().equals("pending"))) {
                        notifications.get(i).markAsRead();
                        logger.info("Marked notification as read: " + notifications.get(i).toString());
                    }
                }

                // Send end marker
                out.println("END_OF_NOTIFICATIONS");
            }
        } catch (IOException e) {
            logger.severe("Error sending notifications: " + e.getMessage());
        }

        // Mark the first notification as read (if it's not a pending follow request)
        if (!notifications.get(0).isRead() &&
                !(notifications.get(0).getType().equals("follow_request") &&
                        notifications.get(0).getStatus().equals("pending"))) {
            notifications.get(0).markAsRead();
            logger.info("Marked first notification as read: " + notifications.get(0).toString());
        }

        logger.info("Client " + clientID + " retrieved their notifications");
    }

    /**
     * Handles a response to a follow request
     * @param parameters The parameters for the response (senderID:choice)
     */
    private void handleFollowResponse(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("Error: Invalid parameters format. Expected 'senderID:choice'");
            return;
        }

        String requestorID = parts[0];
        String choice = parts[1];

        // Check if there is a pending follow request from this requestor
        List<Notification> pendingRequests = server.getPendingFollowRequests(clientID);
        boolean requestFound = false;

        for (Notification request : pendingRequests) {
            if (request.getSenderID().equals(requestorID)) {
                requestFound = true;
                break;
            }
        }

        if (!requestFound) {
            out.println("Error: No pending follow request from client " + requestorID);
            return;
        }

        // Process the response based on the choice
        switch (choice) {
            case "1": // Accept and follow back
                // Update the follow request status
                server.updateFollowRequestStatus(requestorID, clientID, "accepted");

                // Create the follow relationship in both directions
                if (fileManager.createFollowRelationship(requestorID, clientID) &&
                        fileManager.createFollowRelationship(clientID, requestorID)) {

                    // Create a notification for the requestor
                    Notification notification = new Notification(
                            clientID,
                            requestorID,
                            "system",
                            clientID + " accepted your follow request and is now following you back."
                    );
                    server.addNotification(notification);

                    out.println("You are now following " + requestorID + " and they are following you.");
                    logger.info("Client " + clientID + " accepted follow request from " + requestorID +
                            " and followed back");
                } else {
                    out.println("Error creating follow relationship. Please try again.");
                }
                break;

            case "2": // Accept but don't follow back
                // Update the follow request status
                server.updateFollowRequestStatus(requestorID, clientID, "accepted");

                // Create one-way follow relationship
                if (fileManager.createFollowRelationship(requestorID, clientID)) {
                    // Create a notification for the requestor
                    Notification notification = new Notification(
                            clientID,
                            requestorID,
                            "system",
                            clientID + " accepted your follow request."
                    );
                    server.addNotification(notification);

                    out.println("You accepted the follow request from " + requestorID);
                    logger.info("Client " + clientID + " accepted follow request from " + requestorID);
                } else {
                    out.println("Error creating follow relationship. Please try again.");
                }
                break;

            case "3": // Reject
                // Update the follow request status
                server.updateFollowRequestStatus(requestorID, clientID, "rejected");

                // Create a notification for the requestor
                Notification notification = new Notification(
                        clientID,
                        requestorID,
                        "system",
                        clientID + " rejected your follow request."
                );
                server.addNotification(notification);

                out.println("You rejected the follow request from " + requestorID);
                logger.info("Client " + clientID + " rejected follow request from " + requestorID);
                break;

            default:
                out.println("Error: Invalid choice. Expected 1, 2, or 3.");
        }
    }


    private void handleRepost(String parameters) {
        try {
            // Parse parameters
            String[] parts = parameters.split(":", 3);
            if (parts.length < 3) {
                out.println("ERROR:Invalid parameters format. Expected 'originalSenderID:postContent:comment'");
                return;
            }

            String originalSenderID = parts[0].trim();
            String originalContent = parts[1].trim();
            String comment = parts[2].trim();

            // Make sure the client directory exists
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }

            // Add timestamp to the repost
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());

            // Format the repost entry
            String formattedRepost = "[" + timestamp + "] REPOST from " + originalSenderID + ": " + originalContent;
            if (!comment.isEmpty()) {
                formattedRepost += "\n[" + timestamp + "] COMMENT: " + comment;
            }

            // Get the Others file path (as a text file, not a directory)
            Path othersPath = Paths.get(FileManager.DATA_FOLDER, clientID, "Others_42" + clientID + ".txt");
            if (!Files.exists(othersPath)) {
                Files.createFile(othersPath);
                logger.info("Created Others file for client " + clientID);
            }

            // Append the repost to the Others file
            Files.write(othersPath, (formattedRepost + System.lineSeparator() + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);

            logger.info("Client " + clientID + " reposted content from " + originalSenderID);

            // Notify our followers about the repost
            List<String> followers = getFollowers();
            if (!followers.isEmpty()) {
                String notificationContent = "reposted from " + originalSenderID + ": " + originalContent;
                if (!comment.isEmpty()) {
                    notificationContent += " with comment: " + comment;
                }

                for (String followerID : followers) {
                    Notification notification = new Notification(
                            clientID,
                            followerID,
                            "post",
                            clientID + " " + notificationContent
                    );
                    server.addNotification(notification);
                    logger.info("Sent repost notification to follower " + followerID);
                }

                logger.info("Notified " + followers.size() + " followers about repost from client " + clientID);
            }

            // Return success message
            out.println("SUCCESS:Repost created successfully!");
        } catch (IOException e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling repost for client " + clientID + ": " + e.getMessage());
        }
    }
    /**
     * Handles a reply command from the client
     * @param parameters The parameters containing the target client ID and the reply content
     */
    private void handleReply(String parameters) {
        // Feature not implemented yet
        out.println("FEATURE NOT IMPLEMENTED: Reply feature will be available in a future update");
        logger.info("Client " + clientID + " attempted to use unimplemented reply feature");
    }

    /**
     * Handles a follow command from the client
     * @param targetID The ID of the client to follow
     */
    private void handleFollow(String targetID) {
        // Feature not implemented yet
        out.println("FEATURE NOT IMPLEMENTED: Follow feature will be available in a future update");
        logger.info("Client " + clientID + " attempted to use unimplemented follow feature");
    }

    /**
     * Handles an unfollow command from the client
     * @param targetID The ID of the client to unfollow
     */

    private void handleUnfollow(String targetID) {
        // Check if the target client exists
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }

        // Check if the client is actually following the target
        boolean isFollowing = fileManager.isFollowing(clientID, targetID);
        if (!isFollowing) {
            out.println("Error: You are not following client " + targetID + ".");
            return;
        }

        // Remove the follow relationship
        if (fileManager.removeFollowRelationship(clientID, targetID)) {
            // Send a notification to the target user
            Notification notification = new Notification(
                    clientID,
                    targetID,
                    "system",
                    clientID + " has unfollowed you."
            );
            server.addNotification(notification);

            out.println("You have unfollowed client " + targetID + ".");
            logger.info("Client " + clientID + " unfollowed client " + targetID);
        } else {
            out.println("Error: Failed to unfollow client " + targetID + ". Please try again.");
            logger.warning("Failed to remove follow relationship: " + clientID + " -> " + targetID);
        }
    }

    private void handleUpload(String parameters) {
        try {
            // Parse parameters: filename:englishDescription:greekDescription
            String[] parts = parameters.split(":", 3);
            if (parts.length < 2) {
                out.println("Error: Invalid parameters format. Expected 'filename:description_en[:description_gr]'");
                return;
            }

            String fileName = parts[0].trim();
            String descriptionEn = parts[1].trim();
            String descriptionGr = parts.length == 3 ? parts[2].trim() : "";

            if (descriptionEn.isEmpty() && descriptionGr.isEmpty()) {
                out.println("Error: At least one description (EN or GR) must be provided");
                return;
            }

            // Check if the file name is valid
            if (fileName.isEmpty()) {
                out.println("Error: File name cannot be empty");
                return;
            }

            // Create client directory if it doesn't exist
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }

            // Create photos directory if it doesn't exist
            Path photosDir = Paths.get(FileManager.DATA_FOLDER, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
            }

            // Paths for the photo and description files
            Path photoPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", fileName);
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            Path descriptionEnPath = Paths.get(FileManager.DATA_FOLDER, clientID,
                    "photos", baseName + "_en.txt");
            Path descriptionGrPath = Paths.get(FileManager.DATA_FOLDER, clientID,
                    "photos", baseName + "_gr.txt");

            // Tell the client we're ready for the photo
            out.println("READY_FOR_PHOTO");

            // Read the photo file size first
            String fileSizeStr = in.readLine();
            if (fileSizeStr == null) {
                logger.severe("Client " + clientID + " disconnected during upload");
                return;
            }

            long fileSize;
            try {
                fileSize = Long.parseLong(fileSizeStr);
            } catch (NumberFormatException e) {
                out.println("ERROR:Invalid file size format");
                logger.severe("Invalid file size format from client " + clientID + ": " + fileSizeStr);
                return;
            }

            logger.info("Receiving file of size " + fileSize + " bytes from client " + clientID);

            // Prepare to receive file data
            byte[] photoData = new byte[(int) fileSize];
            int bytesRead;
            int totalBytesRead = 0;

            // Create a byte array input stream from the socket
            InputStream inputStream = clientSocket.getInputStream();

            // Notify client to start sending data
            out.println("START_SENDING");

            // Read the file data in chunks
            byte[] buffer = new byte[8192]; // 8KB buffer
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (totalBytesRead < fileSize) {
                bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, (int)fileSize - totalBytesRead));
                if (bytesRead == -1) {
                    break;
                }
                baos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Log progress for large files
                if (fileSize > 1024*1024 && totalBytesRead % (1024*1024) == 0) {
                    logger.info("Received " + (totalBytesRead / (1024*1024)) + "MB of " + (fileSize / (1024*1024)) + "MB");
                }

            }

            photoData = baos.toByteArray();

            // Save the photo file
            Files.write(photoPath, photoData);

            // Save the description files based on provided languages
            if (!descriptionEn.isEmpty()) {
                Files.write(descriptionEnPath, descriptionEn.getBytes());
            }
            if (!descriptionGr.isEmpty()) {
                Files.write(descriptionGrPath, descriptionGr.getBytes());
            }

            // Update the profile with the upload notification
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedPost = "[" + timestamp + "] " + clientID + " posted " + fileName;

            // Update the client's profile file
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }

            // Append the post to the profile file
            Files.write(profilePath, (formattedPost + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);

            // Log the successful upload
            logger.info("Client " + clientID + " uploaded photo: " + fileName + " (" + totalBytesRead + " bytes)");

            // Send success response to client

            out.println("SUCCESS:Photo and description uploaded successfully. Profile updated.");
            logger.info("About to notify followers for post: " + formattedPost);

            notifyFollowersAboutPost(formattedPost);

            // Update followers' Others files with this post and preferred language description
            List<String> followers = getFollowers();
            for (String followerID : followers) {
                Path followerOthersPath = Paths.get(FileManager.DATA_FOLDER, followerID,
                        "Others_42" + followerID + ".txt");
                if (!Files.exists(followerOthersPath)) {
                    Files.createFile(followerOthersPath);
                }

                String followerLang = getLanguagePreferenceFor(followerID);
                String descriptionForFollower = readDescriptionForLanguage(descriptionEnPath,
                        descriptionGrPath, followerLang);

                String entry = formattedPost;
                if (!descriptionForFollower.isEmpty()) {
                    entry += System.lineSeparator() + descriptionForFollower;
                }

                Files.write(followerOthersPath,
                        (entry + System.lineSeparator()).getBytes(),
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling file upload from client " + clientID + ": " + e.getMessage());
        }

    }


    /**
     * Transfers a file to the client
     * @param fileName The name of the file to transfer
     * @param sourceClientID The ID of the client who owns the file
     */
    private void transferFile(String fileName, String sourceClientID) {
        try {
            logger.info("Starting file transfer of " + fileName + " from client " + sourceClientID +
                    " to client " + clientID);

            // Get the file paths
            Path photoPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", fileName);
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            Path descriptionEnPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos",
                    baseName + "_en.txt");
            Path descriptionGrPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos",
                    baseName + "_gr.txt");

            Path descriptionPath;
            if (languagePreference.equals("gr") && Files.exists(descriptionGrPath)) {
                descriptionPath = descriptionGrPath;
            } else if (Files.exists(descriptionEnPath)) {
                descriptionPath = descriptionEnPath;
            } else if (Files.exists(descriptionGrPath)) {
                descriptionPath = descriptionGrPath;
            } else {
                descriptionPath = null;
            }

            // Check if the files exist
            if (!Files.exists(photoPath)) {
                out.println("ERROR:Photo file not found");
                logger.warning("Photo file not found: " + photoPath);
                return;
            }

            // Read the photo file
            byte[] photoData = Files.readAllBytes(photoPath);

            // Calculate the number of chunks to send
            int numChunks = 10; // As per specification, approximately 10 chunks
            int chunkSize = (int) Math.ceil((double) photoData.length / numChunks);

            // Send the number of chunks and total file size to the client
            out.println("FILE_INFO:" + numChunks + ":" + photoData.length);

            // Wait for acknowledgment
            String response = in.readLine();
            if (!response.equals("FILE_INFO_ACK")) {
                logger.warning("Client did not acknowledge file info");
                out.println("ERROR:File transfer aborted");
                return;
            }

            // Send each chunk with stop-and-wait protocol
            for (int i = 0; i < numChunks; i++) {
                int startPos = i * chunkSize;
                int endPos = Math.min(startPos + chunkSize, photoData.length);
                int actualChunkSize = endPos - startPos;

                // Create a chunk of data
                byte[] chunk = new byte[actualChunkSize];
                System.arraycopy(photoData, startPos, chunk, 0, actualChunkSize);

                // Send the chunk
                sendFileChunk(i + 1, chunk, numChunks);
            }

            // Send the description file (in one chunk since it's small)
            if (descriptionPath != null && Files.exists(descriptionPath)) {
                byte[] descriptionData = Files.readAllBytes(descriptionPath);

                // Send the description
                out.println("DESCRIPTION:" + descriptionData.length);

                // Wait for acknowledgment
                response = in.readLine();
                if (!response.equals("DESCRIPTION_ACK")) {
                    logger.warning("Client did not acknowledge description file info");
                    return;
                }

                // Send the description data
                out.println(new String(descriptionData, "UTF-8"));

                // Wait for acknowledgment
                response = in.readLine();
                if (!response.equals("DESCRIPTION_RECEIVED")) {
                    logger.warning("Client did not acknowledge description file receipt");
                    return;
                }

                logger.info("Description file sent successfully");
            } else {
                // Notify client that there's no description file
                out.println("NO_DESCRIPTION");
                logger.info("No description file found for " + fileName);

                // Wait for acknowledgment
                response = in.readLine();
                if (!response.equals("NO_DESCRIPTION_ACK")) {
                    logger.warning("Client did not acknowledge no description message");
                    return;
                }
            }

            // Notify client that the transmission is complete
            out.println("TRANSFER_COMPLETE");
            logger.info("File transfer of " + fileName + " completed successfully");

            // Synchronize client's directory on the server
            synchronizeClientDirectory(fileName, sourceClientID);

        } catch (IOException e) {
            out.println("ERROR:File transfer failed: " + e.getMessage());
            logger.severe("Error transferring file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Synchronizes the client's directory on the server
     * @param fileName The name of the file that was transferred
     * @param sourceClientID The ID of the client who owned the original file
     */
    private void synchronizeClientDirectory(String fileName, String sourceClientID) {
        try {
            logger.info("Synchronizing client directory on server for " + clientID);

            // Create photos directory if it doesn't exist
            Path clientPhotosDir = Paths.get(FileManager.DATA_FOLDER, clientID, "photos");
            if (!Files.exists(clientPhotosDir)) {
                Files.createDirectories(clientPhotosDir);
                logger.info("Created photos directory for client " + clientID);
            }

            // Copy the photo file from source client to the current client
            Path sourcePhotoPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", fileName);
            Path targetPhotoPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", fileName);

            if (Files.exists(sourcePhotoPath) && !Files.exists(targetPhotoPath)) {
                Files.copy(sourcePhotoPath, targetPhotoPath);
                logger.info("Copied photo file " + fileName + " to client " + clientID + "'s directory");
            }

            // Copy the description files if they exist
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            Path sourceDescEnPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", baseName + "_en.txt");
            Path targetDescEnPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", baseName + "_en.txt");
            Path sourceDescGrPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", baseName + "_gr.txt");
            Path targetDescGrPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", baseName + "_gr.txt");

            if (Files.exists(sourceDescEnPath) && !Files.exists(targetDescEnPath)) {
                Files.copy(sourceDescEnPath, targetDescEnPath);
                logger.info("Copied EN description file for " + fileName + " to client " + clientID + "'s directory");
            }
            if (Files.exists(sourceDescGrPath) && !Files.exists(targetDescGrPath)) {
                Files.copy(sourceDescGrPath, targetDescGrPath);
                logger.info("Copied GR description file for " + fileName + " to client " + clientID + "'s directory");
            }

            // Update profile to reflect the download
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedMessage = "[" + timestamp + "] " + clientID + " downloaded " + fileName +
                    " from " + sourceClientID;

            // Get the profile file path
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);

            // Create the file if it doesn't exist
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }

            // Append the message to the profile file
            Files.write(profilePath, (formattedMessage + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);

            logger.info("Client directory synchronized on server");

        } catch (IOException e) {
            logger.severe("Error synchronizing client directory: " + e.getMessage());
        }
    }

    /**
     * Sends a chunk of file data to the client with timeout and retry mechanism
     * @param chunkNumber The chunk number (starting from 1)
     * @param chunkData The chunk data to send
     * @param totalChunks The total number of chunks
     * @throws IOException If an error occurs during transmission
     */
    private void sendFileChunk(int chunkNumber, byte[] chunkData, int totalChunks) throws IOException {
        final int MAX_RETRIES = 3;
        final int TIMEOUT_MS = 5000; // 5 seconds timeout

        boolean acknowledged = false;
        int retries = 0;

        while (!acknowledged && retries < MAX_RETRIES) {
            // Encode the chunk data to Base64 for text transmission
            String encodedData = Base64.getEncoder().encodeToString(chunkData);

            // Send the chunk header
            out.println("CHUNK:" + chunkNumber + ":" + totalChunks + ":" + encodedData.length());

            // Send the chunk data
            out.println(encodedData);

            logger.info("Sent chunk " + chunkNumber + "/" + totalChunks + " (" + chunkData.length + " bytes)");

            // Special handling for chunk 3 (expected to timeout on first try)
            if (chunkNumber == 3 && retries == 0) {
                logger.info("Chunk 3: Expecting no ACK from client (per specification)");
            }

            // Special handling for chunk 6 (expected to receive delayed ACK)
            if (chunkNumber == 6 && retries == 0) {
                logger.info("Chunk 6: Expecting delayed ACK from client (per specification)");
            }

            // Set up the timeout timer
            long startTime = System.currentTimeMillis();
            boolean timedOut = false;

            // Wait for acknowledgment with timeout
            String response = null;
            try {
                // Set socket timeout
                clientSocket.setSoTimeout(TIMEOUT_MS);

                try {
                    response = in.readLine();
                } catch (SocketTimeoutException e) {
                    timedOut = true;
                } finally {
                    // Reset socket timeout
                    clientSocket.setSoTimeout(0);
                }

                // Process timeout condition
                if (timedOut) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.warning("Server did not receive ACK for chunk " + chunkNumber +
                            " (timeout after " + elapsedTime + "ms)");

                    // Explicitly log the message as required for chunk 3
                    if (chunkNumber == 3 && retries == 0) {
                        System.out.println("Server did not receive ACK");
                        logger.warning("Server did not receive ACK");
                    }

                    retries++;
                    continue;
                }

                // Process response
                if (response != null && response.equals("CHUNK_ACK:" + chunkNumber)) {
                    acknowledged = true;
                    logger.info("Received ACK for chunk " + chunkNumber);

                    // Special handling for chunk 6 (handle potential duplicate ACKs)
                    if (chunkNumber == 6) {
                        // After receiving the first ACK, set a short timeout to check for duplicate ACKs
                        clientSocket.setSoTimeout(1000);
                        try {
                            String duplicateResponse = in.readLine();
                            if (duplicateResponse != null && duplicateResponse.equals("CHUNK_ACK:" + chunkNumber)) {
                                // Correctly handle duplicate ACK by ignoring it
                                logger.info("Received duplicate ACK for chunk 6 (as expected)");
                            }
                        } catch (SocketTimeoutException e) {
                            // No duplicate ACK received, which is fine
                        } finally {
                            // Reset socket timeout
                            clientSocket.setSoTimeout(0);
                        }
                    }
                } else {
                    logger.warning("Received invalid response for chunk " + chunkNumber + ": " + response);
                    retries++;
                }
            } catch (IOException e) {
                if (!(e instanceof SocketTimeoutException)) {
                    logger.severe("IO error while sending chunk " + chunkNumber + ": " + e.getMessage());
                    throw e;
                }
            }
        }

        if (!acknowledged) {
            throw new IOException("Failed to send chunk " + chunkNumber + " after " + MAX_RETRIES + " attempts");
        }
    }
    /**
     * Closes the connection with the client
     */
    private void closeConnection() {
        try {
            if (clientID != null && authenticated) {
                server.removeClientFromCatalog(clientID);
                logger.info("Client " + clientID + " disconnected");
            }

            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            logger.severe("Error closing connection: " + e.getMessage());
        }
    }

    private void notifyFollowersAboutPost(String postContent) {
        try {
            // Add debug log at the start
            logger.info("Starting notification process for post: " + postContent);

            // Get the list of followers from the social graph file
            List<String> followers = getFollowers();

            // Add debug log about followers
            logger.info("Found " + followers.size() + " followers for client " + clientID + ": " + String.join(", ", followers));

            // Create and send a notification to each follower
            for (String followerID : followers) {
                Notification notification = new Notification(
                        clientID,                // sender is current client
                        followerID,              // receiver is the follower
                        "post",                  // notification type
                        clientID + " posted: " + postContent  // notification content
                );

                // Add the notification to the server's notification system
                server.addNotification(notification);

                logger.info("Sent post notification to follower " + followerID);
            }

            if (!followers.isEmpty()) {
                logger.info("Notified " + followers.size() + " followers about new post from " + clientID);
            } else {
                logger.info("Client " + clientID + " has no followers to notify");
            }
        } catch (IOException e) {
            logger.severe("Error notifying followers about post: " + e.getMessage());
            e.printStackTrace(); // Add stack trace for better debugging
        }
    }


    /**
     * Gets the list of followers for the specified client
     * @param userID The ID of the client whose followers are requested
     * @return A list of follower IDs
     */
    private List<String> getFollowersOf(String userID) throws IOException {
        List<String> followers = new ArrayList<>();

        // Read the social graph file
        Path socialGraphPath = Paths.get(FileManager.DATA_FOLDER, FileManager.SOCIAL_GRAPH_FILENAME);
        if (!Files.exists(socialGraphPath)) {
            logger.warning("Social graph file does not exist: " + socialGraphPath);
            return followers;
        }

        // Add debug log
        logger.info("Reading social graph file: " + socialGraphPath);

        List<String> lines = Files.readAllLines(socialGraphPath);

        // Add debug log
        logger.info("Social graph contains " + lines.size() + " lines");
        for (String line : lines) {
            logger.info("Social graph entry: " + line);
        }

        // Look for the line with the given client ID
        boolean clientFound = false;
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0 && parts[0].equals(userID)) {
                clientFound = true;
                // Get all followers (all elements after the first element)
                for (int i = 1; i < parts.length; i++) {
                    followers.add(parts[i]);
                }
                break;
            }
        }

        if (!clientFound) {
            logger.warning("Client " + userID + " not found in social graph");
        }

        return followers;
    }

    /**
     * Convenience method to get followers of the currently authenticated client.
     */
    private List<String> getFollowers() throws IOException {
        return getFollowersOf(clientID);
    }

    /** Loads the client's language preference from file. */
    private void loadLanguagePreference() {
        Path prefPath = Paths.get(FileManager.DATA_FOLDER, clientID, "language.txt");
        if (Files.exists(prefPath)) {
            try {
                List<String> lines = Files.readAllLines(prefPath);
                if (!lines.isEmpty()) {
                    languagePreference = lines.get(0).trim().toLowerCase();
                }
            } catch (IOException e) {
                logger.warning("Unable to read language preference for " + clientID + ": " + e.getMessage());
            }
        }
    }

    /** Saves the client's language preference to file. */
    private void saveLanguagePreference() {
        try {
            Path prefPath = Paths.get(FileManager.DATA_FOLDER, clientID, "language.txt");
            Files.write(prefPath, languagePreference.getBytes());
        } catch (IOException e) {
            logger.warning("Unable to save language preference for " + clientID + ": " + e.getMessage());
        }
    }

    /** Handles a request from the client to change language preference. */
    private void handleSetLanguage(String lang) {
        lang = lang.trim().toLowerCase();
        if (!lang.equals("en") && !lang.equals("gr")) {
            out.println("ERROR:Invalid language. Use 'en' or 'gr'");
            return;
        }
        languagePreference = lang;
        saveLanguagePreference();
        out.println("SUCCESS:Language preference updated to " + lang);
    }

/**
 * Reads the language preference for a specific client. Defaults to "en" if
 * no preference is stored.
 */
private String getLanguagePreferenceFor(String id) {
    Path prefPath = Paths.get(FileManager.DATA_FOLDER, id, "language.txt");
    if (Files.exists(prefPath)) {
        try {
            List<String> lines = Files.readAllLines(prefPath);
            if (!lines.isEmpty()) {
                String lang = lines.get(0).trim().toLowerCase();
                if (lang.equals("gr")) {
                    return "gr";
                }
            }
        } catch (IOException e) {
            logger.warning("Unable to read language preference for " + id + ": " + e.getMessage());
        }
    }
    return "en";
}

/**
 * Returns the appropriate description text for the follower based on
 * language preference.
 */
private String readDescriptionForLanguage(Path enPath, Path grPath, String lang) {
    Path chosen = null;
    if ("gr".equals(lang) && Files.exists(grPath)) {
        chosen = grPath;
    } else if (Files.exists(enPath)) {
        chosen = enPath;
    } else if (Files.exists(grPath)) {
        chosen = grPath;
    }

    if (chosen != null) {
        try {
            return new String(Files.readAllBytes(chosen));
        } catch (IOException e) {
            logger.warning("Unable to read description file " + chosen + ": " + e.getMessage());
        }
    }
    return "";
}

    private void handleAskComment(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("Error: Invalid parameters. Expected 'targetID:comment'");
            return;
        }

        String targetID = parts[0].trim();
        String comment = parts[1].trim();

        // verify target exists
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }

        // verify we follow the target
        if (!fileManager.isFollowing(clientID, targetID)) {
            out.println("Error: You must follow " + targetID + " to comment on their posts.");
            return;
        }

        Notification notification = new Notification(
                clientID,
                targetID,
                "comment_request",
                clientID + " wants to post comment: " + comment
        );
        server.addNotification(notification);

        out.println("Comment request sent to " + targetID + ".");
    }

    private void handleApproveComment(String parameters) {
        String[] parts = parameters.split(":", 3);
        if (parts.length < 2) {
            out.println("Error: Invalid parameters. Expected 'requestorID:response:comment'");
            return;
        }

        String requestorID = parts[0].trim();
        String response = parts[1].trim().toLowerCase();
        String comment = parts.length == 3 ? parts[2].trim() : "";

        if (!fileManager.clientExists(requestorID)) {
            out.println("Error: Client " + requestorID + " does not exist.");
            return;
        }

        boolean approved = response.equals("yes");

        if (!isCommentInPreferredLanguage(comment)) {
            approved = false;
        }

        String content;
        if (approved) {
            content = clientID + " approved your comment: " + comment;
        } else {
            content = clientID + " rejected your comment: " + comment;
        }

        Notification notification = new Notification(
                clientID,
                requestorID,
                "comment_response",
                content
        );
        server.addNotification(notification);

        out.println("Your response has been sent to " + requestorID + ".");
    }

    private void handleComment(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("Error: Invalid parameters. Expected 'targetID:comment'");
            return;
        }

        String targetID = parts[0].trim();
        String comment = parts[1].trim();

        try {
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());

            // Format the entry to clearly state who commented on whose post
            String formatted = "[" + timestamp + "] " + clientID +
                    " commented on " + targetID + "'s post: " + comment;

            Files.write(profilePath, (formatted + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);

            out.println("COMMENT_POSTED:" + formatted);

            // Gather followers of the commenter and of the original poster
            List<String> commenterFollowers = getFollowers();
            List<String> targetFollowers = getFollowersOf(targetID);

            // Combine them into a unique set
            Set<String> allFollowers = new LinkedHashSet<>(commenterFollowers);
            allFollowers.addAll(targetFollowers);

            // Notify all followers about the comment
            for (String followerID : allFollowers) {
                Notification notification = new Notification(
                        clientID,
                        followerID,
                        "post",
                        clientID + " posted: " + formatted
                );
                server.addNotification(notification);

                Path followerOthersPath = Paths.get(FileManager.DATA_FOLDER, followerID,
                        "Others_42" + followerID + ".txt");
                if (!Files.exists(followerOthersPath)) {
                    Files.createFile(followerOthersPath);
                }
                Files.write(followerOthersPath,
                        (formatted + System.lineSeparator()).getBytes(),
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private boolean isCommentInPreferredLanguage(String comment) {
        boolean hasGreek = comment.codePoints()
                .anyMatch(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.GREEK);

        if ("gr".equals(languagePreference)) {
            return hasGreek;
        }

        return !hasGreek; // assume English otherwise
    }

}