package server;
import model.Notification;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
// Handles client sessions and protocol logic.
public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    static {
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
    private String languagePreference = "en";
    public ClientHandler(Socket socket, SocialNetworkServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.fileManager = new FileManager();
        this.authenticated = false;
    }
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            processClientCommands();
        } catch (IOException e) {
            logger.severe("Error handling client connection: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    private void processClientCommands() throws IOException {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            if (inputLine.equals("exit")) {
                break;
            }
            String[] parts = inputLine.split(":", 2);
            if (parts.length != 2) {
                out.println("Error: Invalid command format");
                continue;
            }
            String command = parts[0].trim();
            String parameters = parts[1].trim();
            if (command.equals("login") || command.equals("signup")) {
                handleAuthentication(command, parameters);
                continue;
            }
            if (!authenticated) {
                out.println("Error: Please login or signup first");
                continue;
            }
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
                case "ask_photo":
                    handleAskPhoto(parameters);
                    break;
                case "permit_photo":
                    handlePermitPhoto(parameters);
                    break;
                case "photo_details":
                    handlePhotoDetails(parameters);
                    break;
                case "comment":
                    handleComment(parameters);
                    break;
                default:
                    out.println("Error: Unknown command");
            }
        }
    }
    private void handleAuthentication(String command, String clientID) {
        this.clientID = clientID;
        if (command.equals("login")) {
            if (fileManager.clientExists(clientID)) {
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
            if (fileManager.clientExists(clientID)) {
                out.println("Error: Client ID already exists. Please choose another one or login.");
            } else {
                if (fileManager.initializeClientFiles(clientID)) {
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
        if (!this.clientID.equals(clientID)) {
            out.println("Error: Client ID mismatch");
            return;
        }
        boolean syncResult = ClientServerSynchronizer.synchronizeClientData(clientID);
        if (syncResult) {
            out.println("Data synchronized successfully");
        } else {
            out.println("Synchronization failed or was incomplete");
        }
    }
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
            List<String> following = new ArrayList<>();
            Path socialGraphPath = Paths.get(FileManager.DATA_FOLDER, FileManager.SOCIAL_GRAPH_FILENAME);
            if (Files.exists(socialGraphPath)) {
                List<String> graphLines = Files.readAllLines(socialGraphPath);
                for (String line : graphLines) {
                    String[] lineParts = line.split("\\s+");
                    if (lineParts.length > 0) {
                        String lineClientID = lineParts[0];
                        for (int i = 1; i < lineParts.length; i++) {
                            if (lineParts[i].equals(clientID)) {
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
    private void handleDownloadSyn(String clientID) {
        try {
            logger.info("Received DOWNLOAD_SYN from client " + this.clientID);
            if (!this.clientID.equals(clientID)) {
                logger.warning("Client ID mismatch in handshake. Expected: " + this.clientID + ", received: " + clientID);
                out.println("ERROR:Client ID mismatch");
                return;
            }
            String sequenceNumber = String.valueOf(System.currentTimeMillis());
            this.downloadSequenceNumber = sequenceNumber;
            logger.info("Handshake Step 1/3: Client " + this.clientID + " initiated 3-way handshake (SYN)");
            out.println("SYN_ACK:" + sequenceNumber);
            logger.info("Handshake Step 2/3: Sent SYN-ACK to client " + clientID + " with sequence number " + sequenceNumber);
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download SYN: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void handleDownloadAck(String parameters) {
        try {
            logger.info("Received DOWNLOAD_ACK from client " + this.clientID + " with parameters: " + parameters);
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
            if (!receivedSequence.equals(this.downloadSequenceNumber)) {
                logger.warning("Sequence number mismatch. Expected: " + this.downloadSequenceNumber +
                        ", received: " + receivedSequence);
                out.println("ERROR:Sequence number mismatch");
                return;
            }
            if (!fileName.equals(this.downloadFileName) || !sourceClientID.equals(this.downloadSourceClientID)) {
                logger.warning("File or source client mismatch. Expected: " + this.downloadFileName +
                        " from " + this.downloadSourceClientID + ", received: " + fileName +
                        " from " + sourceClientID);
                out.println("ERROR:File or source client mismatch");
                return;
            }
            logger.info("3-way handshake completed successfully for download of " + fileName +
                    " from client " + sourceClientID);
            out.println("TRANSFER_READY");
            transferFile(fileName, sourceClientID);
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download ACK: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void handleDownload(String parameters) {
        try {
            logger.info("Received DOWNLOAD request from client " + clientID + " with parameters: " + parameters);
            String[] parts = parameters.split(":", 2);
            if (parts.length != 2) {
                logger.warning("Invalid download parameters: " + parameters);
                out.println("ERROR:Invalid parameters format. Expected 'fileName:sourceClientID'");
                return;
            }
            String fileName = parts[0].trim();
            String sourceClientID = parts[1].trim();
            logger.info("Client " + clientID + " requested to download " + fileName + " from client " + sourceClientID);
            if (!fileManager.clientExists(sourceClientID)) {
                logger.warning("Source client " + sourceClientID + " does not exist");
                out.println("ERROR:Source client " + sourceClientID + " does not exist");
                return;
            }
            boolean isFollowing = fileManager.isFollowing(clientID, sourceClientID);
            if (!isFollowing) {
                logger.warning("Client " + clientID + " is not following client " + sourceClientID);
                out.println("ERROR:You are not following client " + sourceClientID);
                return;
            }
            Path photoPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", fileName);
            if (!Files.exists(photoPath)) {
                logger.warning("File " + fileName + " not found in client " + sourceClientID + "'s directory");
                out.println("ERROR:File " + fileName + " not found in client " + sourceClientID + "'s directory");
                return;
            }
            if (!server.hasPhotoAccess(sourceClientID, clientID, fileName)) {
                out.println("ERROR:Access to " + fileName + " not permitted by " + sourceClientID);
                return;
            }
            server.revokePhotoAccess(sourceClientID, clientID, fileName);
            this.downloadFileName = fileName;
            this.downloadSourceClientID = sourceClientID;
            logger.info("Preparing to initiate 3-way handshake for download of " + fileName +
                    " from client " + sourceClientID);
            out.println("HANDSHAKE_INIT");
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling download request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void handlePost(String content) {
        try {
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedPost = "[" + timestamp + "] " + content;
            Files.write(profilePath, (formattedPost + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            logger.info("Client " + clientID + " added post to profile: " + content);
            out.println("Post created successfully! Your profile has been updated with: " + formattedPost);
            logger.info("About to notify followers for upload: " + formattedPost);
            notifyFollowersAboutPost(formattedPost);
        } catch (IOException e) {
            out.println("Error creating post: " + e.getMessage());
            logger.severe("Error updating profile for client " + clientID + ": " + e.getMessage());
        }
    }
    private void handleFollowRequest(String targetID) {
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }
        Notification notification = new Notification(
                clientID,
                targetID,
                "follow_request",
                "wants to follow you."
        );
        server.addNotification(notification);
        out.println("Follow request sent to client " + targetID + ". Waiting for their response.");
        logger.info("Client " + clientID + " sent a follow request to client " + targetID);
    }
    private void handleAccessProfile(String targetID) {
        if (!fileManager.clientExists(targetID)) {
            out.println("ERROR:Client " + targetID + " does not exist.");
            logger.warning("Client " + clientID + " attempted to access non-existent client " + targetID);
            return;
        }
        boolean isFollowing = fileManager.isFollowing(clientID, targetID);
        if (!isFollowing) {
            handleDenyProfile(targetID);
            return;
        }
        try {
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, targetID, "Profile_42" + targetID);
            if (!Files.exists(profilePath)) {
                out.println("ERROR:Profile for client " + targetID + " not found.");
                logger.warning("Profile for client " + targetID + " not found when requested by " + clientID);
                return;
            }
            List<String> profileContent = Files.readAllLines(profilePath);
            StringBuilder response = new StringBuilder();
            response.append("PROFILE_START");
            for (String line : profileContent) {
                response.append("\n").append(line);
            }
            response.append("\nPROFILE_END");
            out.println(response.toString());
            logger.info("Client " + clientID + " accessed profile of client " + targetID);
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
        Notification notification = new Notification(
                clientID,
                targetID,
                "system",
                clientID + " attempted to view your profile but was denied (not following you)."
        );
        server.addNotification(notification);
    }
    private void handleGetNotifications() {
        List<Notification> notifications = server.getClientNotifications(clientID);
        if (notifications.isEmpty()) {
            out.println("No notifications.");
            return;
        }
        out.println(notifications.get(0).toString());
        try {
            String clientResponse = in.readLine();
            if (clientResponse.equals("continue_reading")) {
                for (int i = 1; i < notifications.size(); i++) {
                    out.println(notifications.get(i).toString());
                    if (!notifications.get(i).isRead() &&
                            !(notifications.get(i).getType().equals("follow_request") &&
                                    notifications.get(i).getStatus().equals("pending"))) {
                        notifications.get(i).markAsRead();
                        logger.info("Marked notification as read: " + notifications.get(i).toString());
                    }
                }
                out.println("END_OF_NOTIFICATIONS");
            }
        } catch (IOException e) {
            logger.severe("Error sending notifications: " + e.getMessage());
        }
        if (!notifications.get(0).isRead() &&
                !(notifications.get(0).getType().equals("follow_request") &&
                        notifications.get(0).getStatus().equals("pending"))) {
            notifications.get(0).markAsRead();
            logger.info("Marked first notification as read: " + notifications.get(0).toString());
        }
        logger.info("Client " + clientID + " retrieved their notifications");
    }
    private void handleFollowResponse(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("Error: Invalid parameters format. Expected 'senderID:choice'");
            return;
        }
        String requestorID = parts[0];
        String choice = parts[1];
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
        switch (choice) {
            case "1": 
                server.updateFollowRequestStatus(requestorID, clientID, "accepted");
                if (fileManager.createFollowRelationship(requestorID, clientID) &&
                        fileManager.createFollowRelationship(clientID, requestorID)) {
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
            case "2": 
                server.updateFollowRequestStatus(requestorID, clientID, "accepted");
                if (fileManager.createFollowRelationship(requestorID, clientID)) {
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
            case "3": 
                server.updateFollowRequestStatus(requestorID, clientID, "rejected");
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
            String[] parts = parameters.split(":", 3);
            if (parts.length < 3) {
                out.println("ERROR:Invalid parameters format. Expected 'originalSenderID:postContent:comment'");
                return;
            }
            String originalSenderID = parts[0].trim();
            String originalContent = parts[1].trim();
            String comment = parts[2].trim();
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedRepost = "[" + timestamp + "] REPOST from " + originalSenderID + ": " + originalContent;
            if (!comment.isEmpty()) {
                formattedRepost += "\n[" + timestamp + "] COMMENT: " + comment;
            }
            Path othersPath = Paths.get(FileManager.DATA_FOLDER, clientID, "Others_42" + clientID + ".txt");
            if (!Files.exists(othersPath)) {
                Files.createFile(othersPath);
                logger.info("Created Others file for client " + clientID);
            }
            Files.write(othersPath, (formattedRepost + System.lineSeparator() + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            logger.info("Client " + clientID + " reposted content from " + originalSenderID);
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
            out.println("SUCCESS:Repost created successfully!");
        } catch (IOException e) {
            out.println("ERROR:" + e.getMessage());
            logger.severe("Error handling repost for client " + clientID + ": " + e.getMessage());
        }
    }
    private void handleReply(String parameters) {
        out.println("FEATURE NOT IMPLEMENTED: Reply feature will be available in a future update");
        logger.info("Client " + clientID + " attempted to use unimplemented reply feature");
    }
    private void handleFollow(String targetID) {
        out.println("FEATURE NOT IMPLEMENTED: Follow feature will be available in a future update");
        logger.info("Client " + clientID + " attempted to use unimplemented follow feature");
    }
    private void handleUnfollow(String targetID) {
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }
        boolean isFollowing = fileManager.isFollowing(clientID, targetID);
        if (!isFollowing) {
            out.println("Error: You are not following client " + targetID + ".");
            return;
        }
        if (fileManager.removeFollowRelationship(clientID, targetID)) {
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
            if (fileName.isEmpty()) {
                out.println("Error: File name cannot be empty");
                return;
            }
            Path clientDir = Paths.get(FileManager.DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
            }
            Path photosDir = Paths.get(FileManager.DATA_FOLDER, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
            }
            Path photoPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", fileName);
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            Path descriptionEnPath = Paths.get(FileManager.DATA_FOLDER, clientID,
                    "photos", baseName + "_en.txt");
            Path descriptionGrPath = Paths.get(FileManager.DATA_FOLDER, clientID,
                    "photos", baseName + "_gr.txt");
            out.println("READY_FOR_PHOTO");
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
            byte[] photoData = new byte[(int) fileSize];
            int bytesRead;
            int totalBytesRead = 0;
            InputStream inputStream = clientSocket.getInputStream();
            out.println("START_SENDING");
            byte[] buffer = new byte[8192]; 
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (totalBytesRead < fileSize) {
                bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, (int)fileSize - totalBytesRead));
                if (bytesRead == -1) {
                    break;
                }
                baos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize > 1024*1024 && totalBytesRead % (1024*1024) == 0) {
                    logger.info("Received " + (totalBytesRead / (1024*1024)) + "MB of " + (fileSize / (1024*1024)) + "MB");
                }
            }
            photoData = baos.toByteArray();
            Files.write(photoPath, photoData);
            if (!descriptionEn.isEmpty()) {
                Files.write(descriptionEnPath, descriptionEn.getBytes());
            }
            if (!descriptionGr.isEmpty()) {
                Files.write(descriptionGrPath, descriptionGr.getBytes());
            }
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedPost = "[" + timestamp + "] " + clientID + " posted " + fileName;
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }
            Files.write(profilePath, (formattedPost + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            logger.info("Client " + clientID + " uploaded photo: " + fileName + " (" + totalBytesRead + " bytes)");
            out.println("SUCCESS:Photo and description uploaded successfully. Profile updated.");
            logger.info("About to notify followers for post: " + formattedPost);
            notifyFollowersAboutPost(formattedPost);
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
    private void transferFile(String fileName, String sourceClientID) {
        try {
            logger.info("Starting file transfer of " + fileName + " from client " + sourceClientID +
                    " to client " + clientID);
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
            if (!Files.exists(photoPath)) {
                out.println("ERROR:Photo file not found");
                logger.warning("Photo file not found: " + photoPath);
                return;
            }
            byte[] photoData = Files.readAllBytes(photoPath);
            int numChunks = 10; 
            int chunkSize = (int) Math.ceil((double) photoData.length / numChunks);
            out.println("FILE_INFO:" + numChunks + ":" + photoData.length);
            String response = in.readLine();
            if (!response.equals("FILE_INFO_ACK")) {
                logger.warning("Client did not acknowledge file info");
                out.println("ERROR:File transfer aborted");
                return;
            }
            for (int i = 0; i < numChunks; i++) {
                int startPos = i * chunkSize;
                int endPos = Math.min(startPos + chunkSize, photoData.length);
                int actualChunkSize = endPos - startPos;
                byte[] chunk = new byte[actualChunkSize];
                System.arraycopy(photoData, startPos, chunk, 0, actualChunkSize);
                sendFileChunk(i + 1, chunk, numChunks);
            }
            if (descriptionPath != null && Files.exists(descriptionPath)) {
                byte[] descriptionData = Files.readAllBytes(descriptionPath);
                out.println("DESCRIPTION:" + descriptionData.length);
                response = in.readLine();
                if (!response.equals("DESCRIPTION_ACK")) {
                    logger.warning("Client did not acknowledge description file info");
                    return;
                }
                out.println(new String(descriptionData, "UTF-8"));
                response = in.readLine();
                if (!response.equals("DESCRIPTION_RECEIVED")) {
                    logger.warning("Client did not acknowledge description file receipt");
                    return;
                }
                logger.info("Description file sent successfully");
            } else {
                out.println("NO_DESCRIPTION");
                logger.info("No description file found for " + fileName);
                response = in.readLine();
                if (!response.equals("NO_DESCRIPTION_ACK")) {
                    logger.warning("Client did not acknowledge no description message");
                    return;
                }
            }
            out.println("TRANSFER_COMPLETE");
            logger.info("File transfer of " + fileName + " completed successfully");
            synchronizeClientDirectory(fileName, sourceClientID);
        } catch (IOException e) {
            out.println("ERROR:File transfer failed: " + e.getMessage());
            logger.severe("Error transferring file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void synchronizeClientDirectory(String fileName, String sourceClientID) {
        try {
            logger.info("Synchronizing client directory on server for " + clientID);
            Path clientPhotosDir = Paths.get(FileManager.DATA_FOLDER, clientID, "photos");
            if (!Files.exists(clientPhotosDir)) {
                Files.createDirectories(clientPhotosDir);
                logger.info("Created photos directory for client " + clientID);
            }
            Path sourcePhotoPath = Paths.get(FileManager.DATA_FOLDER, sourceClientID, "photos", fileName);
            Path targetPhotoPath = Paths.get(FileManager.DATA_FOLDER, clientID, "photos", fileName);
            if (Files.exists(sourcePhotoPath) && !Files.exists(targetPhotoPath)) {
                Files.copy(sourcePhotoPath, targetPhotoPath);
                logger.info("Copied photo file " + fileName + " to client " + clientID + "'s directory");
            }
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
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedMessage = "[" + timestamp + "] " + clientID + " downloaded " + fileName +
                    " from " + sourceClientID;
            Path profilePath = Paths.get(FileManager.DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }
            Files.write(profilePath, (formattedMessage + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            logger.info("Client directory synchronized on server");
        } catch (IOException e) {
            logger.severe("Error synchronizing client directory: " + e.getMessage());
        }
    }
    private void sendFileChunk(int chunkNumber, byte[] chunkData, int totalChunks) throws IOException {
        final int MAX_RETRIES = 3;
        final int TIMEOUT_MS = 5000; 
        boolean acknowledged = false;
        int retries = 0;
        while (!acknowledged && retries < MAX_RETRIES) {
            String encodedData = Base64.getEncoder().encodeToString(chunkData);
            out.println("CHUNK:" + chunkNumber + ":" + totalChunks + ":" + encodedData.length());
            out.println(encodedData);
            logger.info("Sent chunk " + chunkNumber + "/" + totalChunks + " (" + chunkData.length + " bytes)");
            if (chunkNumber == 3 && retries == 0) {
                logger.info("Chunk 3: Expecting no ACK from client (per specification)");
            }
            if (chunkNumber == 6 && retries == 0) {
                logger.info("Chunk 6: Expecting delayed ACK from client (per specification)");
            }
            long startTime = System.currentTimeMillis();
            boolean timedOut = false;
            String response = null;
            try {
                clientSocket.setSoTimeout(TIMEOUT_MS);
                try {
                    response = in.readLine();
                } catch (SocketTimeoutException e) {
                    timedOut = true;
                } finally {
                    clientSocket.setSoTimeout(0);
                }
                if (timedOut) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.warning("Server did not receive ACK for chunk " + chunkNumber +
                            " (timeout after " + elapsedTime + "ms)");
                    if (chunkNumber == 3 && retries == 0) {
                        System.out.println("Server did not receive ACK");
                        logger.warning("Server did not receive ACK");
                    }
                    retries++;
                    continue;
                }
                if (response != null && response.equals("CHUNK_ACK:" + chunkNumber)) {
                    acknowledged = true;
                    logger.info("Received ACK for chunk " + chunkNumber);
                    if (chunkNumber == 6) {
                        clientSocket.setSoTimeout(1000);
                        try {
                            String duplicateResponse = in.readLine();
                            if (duplicateResponse != null && duplicateResponse.equals("CHUNK_ACK:" + chunkNumber)) {
                                logger.info("Received duplicate ACK for chunk 6 (as expected)");
                            }
                        } catch (SocketTimeoutException e) {
                        } finally {
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
            logger.info("Starting notification process for post: " + postContent);
            List<String> followers = getFollowers();
            logger.info("Found " + followers.size() + " followers for client " + clientID + ": " + String.join(", ", followers));
            for (String followerID : followers) {
                Notification notification = new Notification(
                        clientID,                
                        followerID,              
                        "post",                  
                        clientID + " posted: " + postContent  
                );
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
            e.printStackTrace(); 
        }
    }
    private List<String> getFollowersOf(String userID) throws IOException {
        List<String> followers = new ArrayList<>();
        Path socialGraphPath = Paths.get(FileManager.DATA_FOLDER, FileManager.SOCIAL_GRAPH_FILENAME);
        if (!Files.exists(socialGraphPath)) {
            logger.warning("Social graph file does not exist: " + socialGraphPath);
            return followers;
        }
        logger.info("Reading social graph file: " + socialGraphPath);
        List<String> lines = Files.readAllLines(socialGraphPath);
        logger.info("Social graph contains " + lines.size() + " lines");
        for (String line : lines) {
            logger.info("Social graph entry: " + line);
        }
        boolean clientFound = false;
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0 && parts[0].equals(userID)) {
                clientFound = true;
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
    private List<String> getFollowers() throws IOException {
        return getFollowersOf(clientID);
    }
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
    private void saveLanguagePreference() {
        try {
            Path prefPath = Paths.get(FileManager.DATA_FOLDER, clientID, "language.txt");
            Files.write(prefPath, languagePreference.getBytes());
        } catch (IOException e) {
            logger.warning("Unable to save language preference for " + clientID + ": " + e.getMessage());
        }
    }
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
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }
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
            String formatted = "[" + timestamp + "] " + clientID +
                    " commented on " + targetID + "'s post: " + comment;
            Files.write(profilePath, (formatted + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            Path targetProfilePath = Paths.get(FileManager.DATA_FOLDER, targetID, "Profile_42" + targetID);
            if (!Files.exists(targetProfilePath)) {
                Files.createFile(targetProfilePath);
            }
            Files.write(targetProfilePath, (formatted + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            out.println("COMMENT_POSTED:" + formatted);
            List<String> commenterFollowers = getFollowers();
            List<String> targetFollowers = getFollowersOf(targetID);
            Set<String> allFollowers = new LinkedHashSet<>(commenterFollowers);
            allFollowers.addAll(targetFollowers);
            allFollowers.add(targetID);
            for (String followerID : allFollowers) {
                Notification notification = new Notification(
                        clientID,
                        followerID,
                        "post",
                        clientID + " commented on " + targetID + "'s post: " + comment
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
        return !hasGreek; 
    }
    private void handleAskPhoto(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("Error: Invalid parameters. Expected 'targetID:fileName'");
            return;
        }
        String targetID = parts[0].trim();
        String fileName = parts[1].trim();
        if (!fileManager.clientExists(targetID)) {
            out.println("Error: Client " + targetID + " does not exist.");
            return;
        }
        Path photoPath = Paths.get(FileManager.DATA_FOLDER, targetID, "photos", fileName);
        if (!Files.exists(photoPath)) {
            out.println("Error: File " + fileName + " not found for client " + targetID);
            return;
        }
        Notification notification = new Notification(
                clientID,
                targetID,
                "photo_request",
                clientID + " requests access to " + fileName
        );
        server.addNotification(notification);
        out.println("Access request sent to " + targetID + ".");
    }
    private void handlePermitPhoto(String parameters) {
        String[] parts = parameters.split(":", 3);
        if (parts.length < 3) {
            out.println("Error: Invalid parameters. Expected 'requestorID:fileName:response'");
            return;
        }
        String requestorID = parts[0].trim();
        String fileName = parts[1].trim();
        String response = parts[2].trim().toLowerCase();
        List<Notification> pending = server.getPendingPhotoRequests(clientID);
        boolean found = false;
        for (Notification n : pending) {
            if (n.getSenderID().equals(requestorID) && n.getContent().contains(fileName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            out.println("Error: No pending photo request from client " + requestorID + ".");
            return;
        }
        if (!fileManager.clientExists(requestorID)) {
            out.println("Error: Client " + requestorID + " does not exist.");
            return;
        }
        boolean approved = response.equals("yes");
        String content;
        if (approved) {
            server.grantPhotoAccess(clientID, requestorID, fileName);
            content = clientID + " approved your access to " + fileName;
            server.updatePhotoRequestStatus(requestorID, clientID, fileName, "accepted");
        } else {
            content = clientID + " denied your access to " + fileName;
            server.updatePhotoRequestStatus(requestorID, clientID, fileName, "rejected");
        }
        Notification notification = new Notification(
                clientID,
                requestorID,
                "photo_response",
                content
        );
        server.addNotification(notification);
        out.println("Your response has been sent to " + requestorID + ".");
    }
    private void handlePhotoDetails(String parameters) {
        String[] parts = parameters.split(":", 2);
        if (parts.length != 2) {
            out.println("ERROR:Invalid parameters. Expected 'ownerID:fileName'");
            return;
        }
        String ownerID = parts[0].trim();
        String fileName = parts[1].trim();
        if (!fileManager.clientExists(ownerID)) {
            out.println("ERROR:Client " + ownerID + " does not exist.");
            return;
        }
        Path photoPath = Paths.get(FileManager.DATA_FOLDER, ownerID, "photos", fileName);
        if (!Files.exists(photoPath)) {
            out.println("ERROR:File " + fileName + " not found for client " + ownerID);
            return;
        }
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        Path enPath = Paths.get(FileManager.DATA_FOLDER, ownerID, "photos", base + "_en.txt");
        Path grPath = Paths.get(FileManager.DATA_FOLDER, ownerID, "photos", base + "_gr.txt");
        String descEn = "";
        String descGr = "";
        try {
            if (Files.exists(enPath)) descEn = new String(Files.readAllBytes(enPath));
            if (Files.exists(grPath)) descGr = new String(Files.readAllBytes(grPath));
        } catch (IOException e) {
        }
        List<String> comments = new ArrayList<>();
        Path profilePath = Paths.get(FileManager.DATA_FOLDER, ownerID, "Profile_42" + ownerID);
        try {
            if (Files.exists(profilePath)) {
                for (String line : Files.readAllLines(profilePath)) {
                    if (line.contains(fileName) && line.toLowerCase().contains("comment")) {
                        comments.add(line);
                    }
                }
            }
        } catch (IOException e) {
        }
        out.println("Description EN: " + (descEn.isEmpty() ? "N/A" : descEn));
        out.println("Description GR: " + (descGr.isEmpty() ? "N/A" : descGr));
        if (comments.isEmpty()) {
            out.println("No comments found.");
        } else {
            for (String c : comments) {
                out.println(c);
            }
        }
        out.println("PHOTO_DETAILS_END");
    }
}