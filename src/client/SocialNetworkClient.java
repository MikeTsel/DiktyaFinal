package client;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
// Client application for interacting with the social network server.
public class SocialNetworkClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientID;
    private final String serverAddress;
    private final int serverPort;
    private boolean running;
    private boolean loggedIn;
    private String languagePreference = "en";
    private static final String SRC_FOLDER = "src";
    private static final String CLIENT_FOLDER = SRC_FOLDER + File.separator + "client";
    private static final String LOCAL_DATA_DIR = CLIENT_FOLDER + File.separator + "localdata";
    private final Map<String, Set<String>> pendingPhotoRequests = new HashMap<>();
    public SocialNetworkClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.running = false;
        this.loggedIn = false;
    }
    public boolean connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            return false;
        }
    }
    public void disconnect() {
        running = false;
        loggedIn = false;
        try {
            if (out != null) {
                out.println("exit");
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null) {
                socket.close();
            }
            System.out.println("Disconnected from server");
        } catch (IOException e) {
            System.err.println("Error disconnecting from server: " + e.getMessage());
        }
    }
    private void initializeLocalStorage() {
        if (clientID == null || clientID.isEmpty()) {
            return;
        }
        try {
            Path clientPath = Paths.get(CLIENT_FOLDER);
            if (!Files.exists(clientPath)) {
                Files.createDirectories(clientPath);
                System.out.println("Created client directory: " + clientPath.toAbsolutePath());
            }
            Path localDataPath = Paths.get(LOCAL_DATA_DIR);
            if (!Files.exists(localDataPath)) {
                Files.createDirectories(localDataPath);
                System.out.println("Created local data directory: " + localDataPath.toAbsolutePath());
            }
            Path clientDataPath = Paths.get(LOCAL_DATA_DIR, clientID);
            if (!Files.exists(clientDataPath)) {
                Files.createDirectories(clientDataPath);
                System.out.println("Created client data directory: " + clientDataPath.toAbsolutePath());
            }
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
                System.out.println("Created photos directory: " + photosPath.toAbsolutePath());
            }
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
                System.out.println("Created local profile file: " + profilePath.toAbsolutePath());
            }
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                Files.createFile(followersPath);
                System.out.println("Created local followers file: " + followersPath.toAbsolutePath());
            }
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                Files.createFile(followingPath);
                System.out.println("Created local following file: " + followingPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error initializing local storage: " + e.getMessage());
        }
    }
    public boolean handleAuthentication() {
        if (!running) {
            System.err.println("Not connected to server");
            return false;
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please select an option:");
        System.out.println("1. Login");
        System.out.println("2. Signup");
        System.out.print("> ");
        String choice = scanner.nextLine();
        if (choice.equals("1")) {
            System.out.print("Enter your client ID: ");
            clientID = scanner.nextLine();
            String response = sendCommand("login", clientID);
            System.out.println(response);
            if (response.startsWith("Welcome")) {
                loggedIn = true;
                initializeLocalStorage();
                requestServerSync();
                return true;
            } else {
                return false;
            }
        } else if (choice.equals("2")) {
            System.out.print("Enter a new client ID: ");
            clientID = scanner.nextLine();
            String response = sendCommand("signup", clientID);
            System.out.println(response);
            if (response.startsWith("Welcome")) {
                loggedIn = true;
                initializeLocalStorage();
                requestServerSync();
                return true;
            } else {
                return false;
            }
        } else {
            System.out.println("Invalid choice");
            return false;
        }
    }
    public String sendCommand(String command, String parameters) {
        if (!running) {
            return "Error: Not connected to server";
        }
        try {
            out.println(command + ":" + parameters);
            if (command.equals("get_notifications")) {
                String firstLine = in.readLine();
                if (firstLine.equals("No notifications.")) {
                    return firstLine;
                }
                StringBuilder fullResponse = new StringBuilder(firstLine);
                out.println("continue_reading"); 
                String line;
                while ((line = in.readLine()) != null && !line.equals("END_OF_NOTIFICATIONS")) {
                    fullResponse.append("\n").append(line);
                }
                return fullResponse.toString();
            }
            else if (command.equals("access_profile")) {
                String firstLine = in.readLine();
                if (firstLine.startsWith("PROFILE_START")) {
                    StringBuilder profileContent = new StringBuilder(firstLine);
                    String line;
                    while ((line = in.readLine()) != null && !line.equals("PROFILE_END")) {
                        profileContent.append("\n").append(line);
                    }
                    profileContent.append("\nPROFILE_END");
                    return profileContent.toString();
                } else {
                    return firstLine;
                }
            }
            else if (command.equals("photo_details")) {
                StringBuilder details = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null && !line.equals("PHOTO_DETAILS_END")) {
                    details.append(line).append("\n");
                }
                return details.toString().trim();
            }
            else {
                return in.readLine();
            }
        } catch (IOException e) {
            System.err.println("Error sending command: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    private void displayMenu() {
        System.out.println("\n===== Social Network Menu =====");
        System.out.println("1. Post a message");
        System.out.println("2. Follow a user");
        System.out.println("3. Unfollow a user");
        System.out.println("4. Upload a photo");
        System.out.println("5. View notifications");
        System.out.println("6. Access client profile");
        System.out.println("7. View my reposts");
        System.out.println("8. Search for a photo (with language filter)");
        System.out.println("9. Set language preference");
        System.out.println("10. Display help");
        System.out.println("11. Exit");
        System.out.println("=============================");
        System.out.print("Choose an option: ");
    }
    private void displayHelp() {
        System.out.println("\n===== Help Information =====");
        System.out.println("Available commands:");
        System.out.println("  post <message>           - Create a new post");
        System.out.println("  follow <clientID>        - Follow another client");
        System.out.println("  unfollow <clientID>      - Unfollow a client");
        System.out.println("  upload <file:desc_en:desc_gr> - Upload a photo with descriptions");
        System.out.println("  notifications            - View and respond to notifications (including reposting)");
        System.out.println("  access_profile <clientID> - Access another client's profile");
        System.out.println("  search <filename>:<en|gr> - Search for a photo with description language");
        System.out.println("  view_reposts             - View your reposted content");
        System.out.println("  set_language <en|gr>     - Set preferred language");
        System.out.println("  help                     - Display this help message");
        System.out.println("  exit                     - Disconnect and exit");
        System.out.println("============================");
    }
    public void start() {
        if (!connect()) {
            System.err.println("Failed to connect to server");
            return;
        }
        if (!handleAuthentication()) {
            disconnect();
            return;
        }
        processNotificationsForFollows();
        Scanner scanner = new Scanner(System.in);
        while (running && loggedIn) {
            displayMenu();
            String choice = scanner.nextLine();
            switch (choice) {
                case "1": 
                    System.out.print("Enter your message: ");
                    String message = scanner.nextLine();
                    String response = sendCommand("post", message);
                    System.out.println(response);
                    if (response.startsWith("Post created successfully")) {
                        try {
                            String formattedPost = response.substring(response.indexOf("with: ") + 6);
                            Path localProfilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
                            Files.write(localProfilePath, (formattedPost + System.lineSeparator()).getBytes(),
                                    StandardOpenOption.APPEND);
                            System.out.println("Local profile updated.");
                        } catch (IOException | StringIndexOutOfBoundsException e) {
                            System.err.println("Error updating local profile: " + e.getMessage());
                        }
                    }
                    break;
                case "2": 
                    System.out.print("Enter the client ID you want to follow: ");
                    String targetClientID = scanner.nextLine();
                    if (targetClientID.equals(clientID)) {
                        System.out.println("Error: You cannot follow yourself.");
                        break;
                    }
                    response = sendCommand("follow_request", targetClientID);
                    System.out.println(response);
                    break;
                case "3": 
                    System.out.println("\n===== Currently Following =====");
                    try {
                        Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
                        if (Files.exists(followingPath)) {
                            List<String> following = Files.readAllLines(followingPath);
                            if (following.isEmpty()) {
                                System.out.println("You are not following anyone.");
                            } else {
                                for (String user : following) {
                                    System.out.println("- " + user);
                                }
                            }
                        } else {
                            System.out.println("Following list not found. You might not be following anyone.");
                        }
                    } catch (IOException e) {
                        System.err.println("Error reading following list: " + e.getMessage());
                    }
                    System.out.print("\nEnter the client ID you want to unfollow: ");
                    String unfollowID = scanner.nextLine();
                    if (unfollowID.equals(clientID)) {
                        System.out.println("Error: You cannot unfollow yourself.");
                        break;
                    }
                    response = sendCommand("unfollow", unfollowID);
                    System.out.println(response);
                    if (response.startsWith("You have unfollowed")) {
                        removeLocalFollowing(unfollowID);
                    }
                    break;
                case "4": 
                    uploadPhoto(scanner);
                    break;
                case "5": 
                    response = sendCommand("get_notifications", "");
                    System.out.println("\n===== Your Notifications =====");
                    if (response.equals("No notifications.")) {
                        System.out.println(response);
                        break;
                    }
                    String[] notifications = response.split("\\n");
                    List<Integer> postIndices = new ArrayList<>();
                    List<String> postSenders = new ArrayList<>();
                    List<String> postContents = new ArrayList<>();
                    List<String> commentRequestSenders = new ArrayList<>();
                    List<String> commentRequestTexts = new ArrayList<>();
                    List<String> photoRequestSenders = new ArrayList<>();
                    List<String> photoRequestFiles = new ArrayList<>();
                    for (int i = 0; i < notifications.length; i++) {
                        System.out.println((i+1) + ". " + notifications[i]);
                        int timestampEnd = notifications[i].indexOf("]") + 2;
                        if (timestampEnd > 1) {
                            String messageContent = notifications[i].substring(timestampEnd);
                            if (messageContent.contains(" posted:") || messageContent.contains(" posted ")) {
                                postIndices.add(i);
                                int senderEnd = messageContent.indexOf(" posted");
                                if (senderEnd > 0) {
                                    String sender = messageContent.substring(0, senderEnd);
                                    postSenders.add(sender);
                                    String content = messageContent.substring(senderEnd + " posted:".length()).trim();
                                    if (content.isEmpty() && messageContent.contains(" posted ")) {
                                        content = messageContent.substring(senderEnd + " posted ".length()).trim();
                                    }
                                    postContents.add(content);
                                }
                            } else if (messageContent.contains("wants to post comment:")) {
                                int idx = messageContent.indexOf(" wants to post comment:");
                                String sender = messageContent.substring(0, idx);
                                String com = messageContent.substring(idx + " wants to post comment:".length()).trim();
                                commentRequestSenders.add(sender);
                                commentRequestTexts.add(com);
                            } else if (messageContent.contains("requests access to")) {
                                int idx = messageContent.indexOf(" requests access to ");
                                String sender = messageContent.substring(0, idx);
                                String file = messageContent.substring(idx + " requests access to ".length()).trim();
                                photoRequestSenders.add(sender);
                                photoRequestFiles.add(file);
                            } else if (messageContent.contains("commented on")) {
                                updateLocalOthersWithNotification(notifications[i]);
                                appendToLocalProfile(notifications[i]);
                            } else if (messageContent.contains("approved your comment:")) {
                                int idx = messageContent.indexOf(" approved your comment:");
                                String approver = messageContent.substring(0, idx);
                                String com = messageContent.substring(idx + " approved your comment:".length()).trim();
                                String resp = sendCommand("comment", approver + ":" + com);
                                System.out.println(resp.startsWith("COMMENT_POSTED:") ? resp.substring(15) : resp);
                                updateLocalProfileWithComment(resp);
                            } else if (messageContent.contains("rejected your comment:")) {
                                int idx = messageContent.indexOf(" rejected your comment:");
                                String rejector = messageContent.substring(0, idx);
                                System.out.println("Your comment was rejected by " + rejector + ".");
                            } else if (messageContent.contains("approved your access to")) {
                                int idx = messageContent.indexOf(" approved your access to ");
                                String owner = messageContent.substring(0, idx);
                                String file = messageContent.substring(idx + " approved your access to ".length()).trim();
                                System.out.println(owner + " approved your access to " + file + ". You may now download it.");
                                Set<String> pending = pendingPhotoRequests.get(owner);
                                if (pending != null) {
                                    pending.remove(file);
                                    if (pending.isEmpty()) {
                                        pendingPhotoRequests.remove(owner);
                                    }
                                }
                            } else if (messageContent.contains("denied your access to")) {
                                int idx = messageContent.indexOf(" denied your access to ");
                                String owner = messageContent.substring(0, idx);
                                String file = messageContent.substring(idx + " denied your access to ".length()).trim();
                                System.out.println(owner + " denied your access to " + file + ".");
                                Set<String> pending = pendingPhotoRequests.get(owner);
                                if (pending != null) {
                                    pending.remove(file);
                                    if (pending.isEmpty()) {
                                        pendingPhotoRequests.remove(owner);
                                    }
                                }
                            }
                        }
                    }
                    processFollowNotifications(notifications);
                    boolean hasPendingRequests = false;
                    if (response.contains("follow request from")) {
                        hasPendingRequests = true;
                    }
                    System.out.println("\nWhat would you like to do?");
                    int optionNum = 1;
                    int followOption = -1;
                    int repostOption = -1;
                    int commentOption = -1;
                    int commentReqOption = -1;
                    int photoReqOption = -1;
                    if (hasPendingRequests) {
                        followOption = optionNum++;
                        System.out.println(followOption + ". Respond to follow requests");
                    }
                    if (!postIndices.isEmpty()) {
                        repostOption = optionNum++;
                        System.out.println(repostOption + ". Repost content");
                        commentOption = optionNum++;
                        System.out.println(commentOption + ". Comment on a post");
                    }
                    if (!commentRequestSenders.isEmpty()) {
                        commentReqOption = optionNum++;
                        System.out.println(commentReqOption + ". Respond to comment requests");
                    }
                    if (!photoRequestSenders.isEmpty()) {
                        photoReqOption = optionNum++;
                        System.out.println(photoReqOption + ". Respond to photo access requests");
                    }
                    int returnOption = optionNum;
                    System.out.println(returnOption + ". Return to main menu");
                    System.out.print("> ");
                    choice = scanner.nextLine().trim();
                    if (followOption != -1 && choice.equals(Integer.toString(followOption))) {
                        System.out.println("\nPending follow requests:");
                        int requestCount = 0;
                        for (int i = 0; i < notifications.length; i++) {
                            if (notifications[i].contains("You have a follow request from")) {
                                requestCount++;
                                int startIndex = notifications[i].indexOf("request from ") + 13;
                                int endIndex = notifications[i].indexOf(":", startIndex);
                                if (endIndex == -1) endIndex = notifications[i].length();
                                String senderId = notifications[i].substring(startIndex, endIndex);
                                System.out.println(requestCount + ". From: " + senderId);
                            }
                        }
                        if (requestCount == 0) {
                            System.out.println("No pending follow requests found.");
                            break;
                        }
                        System.out.print("Enter the client ID you want to respond to: ");
                        String requestorID = scanner.nextLine();
                        System.out.println("Choose your response:");
                        System.out.println("1. Accept and follow back");
                        System.out.println("2. Accept but don't follow back");
                        System.out.println("3. Reject");
                        System.out.print("> ");
                        String responseChoice = scanner.nextLine();
                        response = sendCommand("follow_response", requestorID + ":" + responseChoice);
                        System.out.println(response);
                        if (responseChoice.equals("1") || responseChoice.equals("2")) {
                            addLocalFollower(requestorID);
                        }
                        if (responseChoice.equals("1")) {
                            addLocalFollowing(requestorID);
                        }
                    }
                    else if (repostOption != -1 && choice.equals(Integer.toString(repostOption))) {
                        if (postIndices.isEmpty()) {
                            System.out.println("No posts found in your notifications.");
                            break;
                        }
                        System.out.println("\nWhich post would you like to repost?");
                        for (int i = 0; i < postIndices.size(); i++) {
                            int notificationIndex = postIndices.get(i);
                            System.out.println((i+1) + ". " + notifications[notificationIndex]);
                        }
                        System.out.print("Enter number (or 0 to cancel): ");
                        int selection;
                        try {
                            selection = Integer.parseInt(scanner.nextLine().trim());
                            if (selection <= 0 || selection > postIndices.size()) {
                                System.out.println("Repost cancelled or invalid selection.");
                                break;
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Repost cancelled.");
                            break;
                        }
                        int selectedIndex = selection - 1;
                        String originalSender = postSenders.get(selectedIndex);
                        String originalContent = postContents.get(selectedIndex);
                        System.out.print("Add a comment to your repost (optional, press Enter to skip): ");
                        String comment = scanner.nextLine().trim();
                        System.out.print("Repost this content? (y/n): ");
                        String confirm = scanner.nextLine().trim().toLowerCase();
                        if (!confirm.equals("y")) {
                            System.out.println("Repost cancelled.");
                            break;
                        }
                        response = sendCommand("repost", originalSender + ":" + originalContent + ":" + comment);
                        if (response.startsWith("SUCCESS:")) {
                            System.out.println(response.substring(8));
                            try {
                                Path othersPath = Paths.get(LOCAL_DATA_DIR, clientID, "Others_42" + clientID + ".txt");
                                if (!Files.exists(othersPath)) {
                                    Files.createFile(othersPath);
                                }
                                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                        .format(new java.util.Date());
                                String formattedRepost = "[" + timestamp + "] REPOST from " + originalSender + ": " + originalContent;
                                if (!comment.isEmpty()) {
                                    formattedRepost += "\n[" + timestamp + "] COMMENT: " + comment;
                                }
                                Files.write(othersPath, (formattedRepost + System.lineSeparator() + System.lineSeparator()).getBytes(),
                                        StandardOpenOption.APPEND);
                                System.out.println("Local others file updated.");
                            } catch (IOException e) {
                                System.err.println("Error updating local others file: " + e.getMessage());
                            }
                        } else if (response.startsWith("ERROR:")) {
                            System.out.println("Error: " + response.substring(6));
                        } else {
                            System.out.println("Unexpected response: " + response);
                        }
                    }
                    else if (commentOption != -1 && choice.equals(Integer.toString(commentOption))) {
                        if (postIndices.isEmpty()) {
                            System.out.println("No posts found in your notifications.");
                            break;
                        }
                        System.out.println("\nWhich post would you like to comment on?");
                        for (int i = 0; i < postIndices.size(); i++) {
                            int notificationIndex = postIndices.get(i);
                            System.out.println((i+1) + ". " + notifications[notificationIndex]);
                        }
                        System.out.print("Enter number (or 0 to cancel): ");
                        int selection;
                        try {
                            selection = Integer.parseInt(scanner.nextLine().trim());
                            if (selection <= 0 || selection > postIndices.size()) {
                                System.out.println("Comment cancelled or invalid selection.");
                                break;
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Comment cancelled.");
                            break;
                        }
                        int selectedIndex = selection - 1;
                        String targetSender = postSenders.get(selectedIndex);
                        System.out.print("Enter your comment: ");
                        String commentText = scanner.nextLine().trim();
                        if (commentText.isEmpty()) {
                            System.out.println("Empty comment. Cancelled.");
                            break;
                        }
                        response = sendCommand("ask_comment", targetSender + ":" + commentText);
                        System.out.println(response);
                    }
                    else if (commentReqOption != -1 && choice.equals(Integer.toString(commentReqOption))) {
                        if (commentRequestSenders.isEmpty()) {
                            System.out.println("No comment requests.");
                            break;
                        }
                        System.out.println("\nPending comment requests:");
                        for (int i = 0; i < commentRequestSenders.size(); i++) {
                            System.out.println((i+1) + ". From " + commentRequestSenders.get(i) + ": " + commentRequestTexts.get(i));
                        }
                        System.out.print("Enter number to respond (0 to cancel): ");
                        int sel;
                        try {
                            sel = Integer.parseInt(scanner.nextLine().trim());
                            if (sel <= 0 || sel > commentRequestSenders.size()) {
                                System.out.println("Cancelled.");
                                break;
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Cancelled.");
                            break;
                        }
                        String requester = commentRequestSenders.get(sel-1);
                        String cmt = commentRequestTexts.get(sel-1);
                        System.out.print("Approve comment? (y/n): ");
                        String ans = scanner.nextLine().trim().toLowerCase();
                        String decision = ans.equals("y") ? "yes" : "no";
                        response = sendCommand("approve_comment", requester + ":" + decision + ":" + cmt);
                        System.out.println(response);
                    }
                    else if (photoReqOption != -1 && choice.equals(Integer.toString(photoReqOption))) {
                        if (photoRequestSenders.isEmpty()) {
                            System.out.println("No photo access requests.");
                            break;
                        }
                        System.out.println("\nPending photo access requests:");
                        for (int i = 0; i < photoRequestSenders.size(); i++) {
                            System.out.println((i+1) + ". From " + photoRequestSenders.get(i) + " for file " + photoRequestFiles.get(i));
                        }
                        System.out.print("Enter number to respond (0 to cancel): ");
                        int sel;
                        try {
                            sel = Integer.parseInt(scanner.nextLine().trim());
                            if (sel <= 0 || sel > photoRequestSenders.size()) {
                                System.out.println("Cancelled.");
                                break;
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Cancelled.");
                            break;
                        }
                        String requester = photoRequestSenders.get(sel-1);
                        String file = photoRequestFiles.get(sel-1);
                        System.out.print("Permit access? (y/n): ");
                        String ans = scanner.nextLine().trim().toLowerCase();
                        String decision = ans.equals("y") ? "yes" : "no";
                        response = sendCommand("permit_photo", requester + ":" + file + ":" + decision);
                        System.out.println(response);
                    }
                    break;
                case "6": 
                    accessProfile(scanner);
                    break;
                case "7": 
                    viewReposts();
                    break;
                case "8": 
                    searchPhoto(scanner);
                    break;
                case "9": 
                    setLanguagePreference(scanner);
                    break;
                case "10":
                    displayHelp();
                    break;
                case "11":
                    disconnect();
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
        scanner.close();
    }
    private void accessProfile(Scanner scanner) {
        System.out.println("\n===== Access Client Profile =====");
        System.out.println("This feature allows you to view the profile of another client that you follow.");
        System.out.println("The server will check if you are following the requested client in the social graph.");
        System.out.print("\nEnter the client ID whose profile you want to access: ");
        String targetID = scanner.nextLine().trim();
        if (targetID.equals(clientID)) {
            System.out.println("You can view your own profile locally at: " +
                    Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID));
            return;
        }
        System.out.println("Sending profile access request to server...");
        String response = sendCommand("access_profile", targetID);
        if (response.startsWith("PROFILE_START")) {
            String profileContent = response.substring("PROFILE_START".length(),
                    response.length() - "PROFILE_END".length());
            System.out.println("\n===== Profile of Client " + targetID + " =====");
            System.out.println(profileContent);
            System.out.println("=============================");
        } else if (response.startsWith("DENIED:")) {
            System.out.println("\n" + response.substring("DENIED:".length()));
            System.out.println("According to the server's social graph, you must follow this client to access their profile.");
            System.out.println("Use option 3 in the main menu to send a follow request if you want to access this profile.");
        } else if (response.startsWith("ERROR:")) {
            System.out.println("\nError: " + response.substring("ERROR:".length()));
        } else {
            System.out.println("\nUnexpected response from server: " + response);
        }
    }
    private void searchPhoto(Scanner scanner) {
        System.out.println("\n===== Search for a Photo =====");
        System.out.println("This will search for photos among users you follow in your social graph.");
        System.out.print("Enter the photo filename to search for: ");
        String fileName = scanner.nextLine().trim();
        if (fileName.isEmpty()) {
            System.out.println("Error: Filename cannot be empty.");
            return;
        }
        System.out.print("Preferred description language (en/gr, optional): ");
        String lang = scanner.nextLine().trim().toLowerCase();
        if (!lang.isEmpty() && !lang.equals("en") && !lang.equals("gr")) {
            System.out.println("Invalid language. Use 'en' or 'gr'.");
            return;
        }
        System.out.println("Searching for photo: " + fileName);
        String params = fileName;
        if (!lang.isEmpty()) {
            params = fileName + ":" + lang;
        }
        String response = sendCommand("search", params);
        if (response.startsWith("RESULT:")) {
            String results = response.substring("RESULT:".length());
            System.out.println("\n===== Search Results =====");
            if (results.contains("No matching photos found") || !results.contains("##ENTRIES##")) {
                System.out.println(results);
                return;
            }
            String[] parts = results.split("##ENTRIES##");
            System.out.println(parts[0]); 
            List<String> clientsWithPhoto = new ArrayList<>();
            if (parts.length > 1) {
                String entries = parts[1].replace("##NEWLINE##", "\n");
                System.out.println(entries);
                String[] lines = entries.split("\n");
                for (String line : lines) {
                    if (line.contains("Client ID:")) {
                        int startIdx = line.indexOf("Client ID:") + 10;
                        int endIdx = line.indexOf(" - File:");
                        if (startIdx > 0 && endIdx > startIdx) {
                            String clientId = line.substring(startIdx, endIdx).trim();
                            clientsWithPhoto.add(clientId);
                        }
                    }
                }
            }
            if (!clientsWithPhoto.isEmpty()) {
                System.out.print("\nSelect the number of the client to view details (1-" + clientsWithPhoto.size() + ", 0 to cancel): ");
                int sel;
                try {
                    sel = Integer.parseInt(scanner.nextLine().trim());
                    if (sel <= 0 || sel > clientsWithPhoto.size()) {
                        System.out.println("Cancelled.");
                        return;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Cancelled.");
                    return;
                }
                String selectedClient = clientsWithPhoto.get(sel-1);
                String details = sendCommand("photo_details", selectedClient + ":" + fileName);
                if (details.startsWith("ERROR:")) {
                    System.out.println(details.substring(6));
                    return;
                }
                System.out.println("\n===== Photo Details =====");
                System.out.println(details);
                System.out.print("Download this photo? (y/n): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                if (!choice.equals("y")) {
                    return;
                }
                String downloadRequest = fileName + ":" + selectedClient;
                String dlResp = sendCommand("download", downloadRequest);
                if (dlResp.startsWith("ERROR:")) {
                    String msg = dlResp.substring(6);
                    System.out.println("Error initiating download: " + msg);
                    if (msg.contains("Access to")) {
                        Set<String> pending = pendingPhotoRequests.getOrDefault(selectedClient, new HashSet<>());
                        if (pending.contains(fileName)) {
                            System.out.println("Access request already pending.");
                        } else {
                            System.out.println("Sending access request...");
                            String reqResp = sendCommand("ask_photo", selectedClient + ":" + fileName);
                            System.out.println(reqResp);
                            pending.add(fileName);
                            pendingPhotoRequests.put(selectedClient, pending);
                        }
                    }
                    return;
                } else if (dlResp.equals("HANDSHAKE_INIT")) {
                    System.out.println("Beginning 3-way handshake with server...");
                    performHandshake(fileName, selectedClient, scanner);
                } else {
                    System.out.println("Unexpected response from server: " + dlResp);
                }
            }
        } else if (response.startsWith("ERROR:")) {
            System.out.println("Error: " + response.substring(6));
        } else {
            System.out.println("Unexpected response from server: " + response);
        }
    }
    private void initiateDownload(String fileName, String sourceClientID, Scanner scanner) {
        System.out.println("\nInitiating download of " + fileName + " from client " + sourceClientID + "...");
        try {
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
                System.out.println("Created photos directory: " + photosPath);
            }
        } catch (IOException e) {
            System.err.println("Error creating directories: " + e.getMessage());
            return;
        }
        String downloadRequest = fileName + ":" + sourceClientID;
        String response = sendCommand("download", downloadRequest);
        if (response.startsWith("ERROR:")) {
            System.out.println("Error initiating download: " + response.substring(6));
            return;
        } else if (response.equals("HANDSHAKE_INIT")) {
            System.out.println("Beginning 3-way handshake with server...");
            performHandshake(fileName, sourceClientID, scanner);
        } else {
            System.out.println("Unexpected response from server: " + response);
        }
    }
    private void performHandshake(String fileName, String sourceClientID, Scanner scanner) {
        try {
            System.out.println("Step 1/3: Sending connection request (SYN)...");
            String response = sendCommand("download_syn", clientID);
            if (!response.startsWith("SYN_ACK:")) {
                System.out.println("Error in handshake: Expected SYN_ACK, got: " + response);
                return;
            }
            System.out.println("Step 2/3: Received connection acknowledgment (SYN-ACK)");
            String sequenceNumber = response.substring("SYN_ACK:".length());
            System.out.println("Step 3/3: Sending acknowledgment with file request (ACK)...");
            response = sendCommand("download_ack", sequenceNumber + ":" + fileName + ":" + sourceClientID);
            if (response.equals("TRANSFER_READY")) {
                System.out.println("Handshake completed successfully. Starting file transfer...");
                receiveFile(fileName, scanner);
            } else {
                System.out.println("Error starting file transfer: " + response);
            }
        } catch (Exception e) {
            System.err.println("Error during handshake: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void receiveFile(String fileName, Scanner scanner) {
        try {
            System.out.println("Waiting for file transfer to begin...");
            String fileInfo = in.readLine();
            if (!fileInfo.startsWith("FILE_INFO:")) {
                System.err.println("Error: Expected file info, got: " + fileInfo);
                return;
            }
            String[] parts = fileInfo.substring("FILE_INFO:".length()).split(":");
            int numChunks = Integer.parseInt(parts[0]);
            int fileSize = Integer.parseInt(parts[1]);
            System.out.println("Receiving file: " + fileName + " (" + fileSize + " bytes in " + numChunks + " chunks)");
            out.println("FILE_INFO_ACK");
            ByteArrayOutputStream photoData = new ByteArrayOutputStream();
            for (int i = 1; i <= numChunks; i++) {
                String chunkHeader = in.readLine();
                if (!chunkHeader.startsWith("CHUNK:")) {
                    System.err.println("Error: Expected chunk header, got: " + chunkHeader);
                    return;
                }
                parts = chunkHeader.substring("CHUNK:".length()).split(":");
                int chunkNumber = Integer.parseInt(parts[0]);
                int totalChunks = Integer.parseInt(parts[1]);
                int chunkSize = Integer.parseInt(parts[2]);
                String encodedData = in.readLine();
                if (chunkNumber == 3) {
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Purposely not sending ACK");
                    String resendHeader = in.readLine();
                    if (resendHeader == null || !resendHeader.startsWith("CHUNK:")) {
                        System.err.println("Error: Expected resent chunk, got: " + resendHeader);
                        return;
                    }
                    parts = resendHeader.substring("CHUNK:".length()).split(":");
                    int resendChunkNumber = Integer.parseInt(parts[0]);
                    if (resendChunkNumber != chunkNumber) {
                        System.err.println("Error: Expected resent chunk " + chunkNumber +
                                ", got chunk " + resendChunkNumber);
                        return;
                    }
                    String resendData = in.readLine();
                    System.out.println("Received resent chunk " + chunkNumber + "/" + totalChunks +
                            " - Now sending ACK");
                    out.println("CHUNK_ACK:" + chunkNumber);
                    encodedData = resendData;
                }
                else if (chunkNumber == 6) {
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Delaying ACK");
                    try {
                        Thread.sleep(3000); 
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Sending delayed ACK for chunk " + chunkNumber);
                    out.println("CHUNK_ACK:" + chunkNumber);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Sending duplicate ACK for chunk " + chunkNumber + " to test server handling");
                    out.println("CHUNK_ACK:" + chunkNumber);
                }
                else {
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Sending ACK");
                    out.println("CHUNK_ACK:" + chunkNumber);
                }
                byte[] chunkData = Base64.getDecoder().decode(encodedData);
                photoData.write(chunkData);
            }
            String descriptionInfo = in.readLine();
            String description = null;
            if (descriptionInfo.startsWith("DESCRIPTION:")) {
                int descriptionSize = Integer.parseInt(descriptionInfo.substring("DESCRIPTION:".length()));
                System.out.println("Receiving description (" + descriptionSize + " bytes)");
                out.println("DESCRIPTION_ACK");
                description = in.readLine();
                out.println("DESCRIPTION_RECEIVED");
                System.out.println("Description received: " + description);
            }
            else if (descriptionInfo.equals("NO_DESCRIPTION")) {
                System.out.println("No description available for this photo");
                out.println("NO_DESCRIPTION_ACK");
            }
            else {
                System.err.println("Error: Expected description info, got: " + descriptionInfo);
                return;
            }
            String completeMsg = in.readLine();
            if (!completeMsg.equals("TRANSFER_COMPLETE")) {
                System.err.println("Error: Expected transfer complete message, got: " + completeMsg);
                return;
            }
            System.out.println("The transmission is completed.");
            Path photosDir = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
            }
            Path photoPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName);
            Files.write(photoPath, photoData.toByteArray());
            if (description != null) {
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                Path descPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", baseName + "_" + languagePreference + ".txt");
                Files.write(descPath, description.getBytes("UTF-8"));
            }
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedMessage = "[" + timestamp + "] Downloaded " + fileName;
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
            Files.write(profilePath, (formattedMessage + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            System.out.println("File saved successfully to: " + photoPath.toAbsolutePath());
            System.out.println("Local profile updated with download information.");
        } catch (IOException e) {
            System.err.println("Error receiving file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void uploadPhoto(Scanner scanner) {
        System.out.println("\n===== Upload a Photo =====");
        try {
            System.out.print("Enter the path to the photo file: ");
            String photoPath = scanner.nextLine();
            File photoFile = new File(photoPath);
            if (!photoFile.exists() || !photoFile.isFile()) {
                System.out.println("Error: File does not exist.");
                return;
            }
            String fileName = photoFile.getName();
            String extension = "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = fileName.substring(lastDot).toLowerCase();
            }
            if (!extension.equals(".jpg") && !extension.equals(".jpeg") &&
                    !extension.equals(".png") && !extension.equals(".gif")) {
                System.out.println("Error: File must be a valid image (jpg, jpeg, png, or gif).");
                return;
            }
            System.out.print("Enter description in English (leave blank if none): ");
            String descriptionEn = scanner.nextLine();
            System.out.print("Enter description in Greek (leave blank if none): ");
            String descriptionGr = scanner.nextLine();
            if (descriptionEn.isEmpty() && descriptionGr.isEmpty()) {
                System.out.println("Error: At least one description must be provided.");
                return;
            }
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
            }
            Path destPhotoPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", photoFile.getName());
            Files.copy(photoFile.toPath(), destPhotoPath, StandardCopyOption.REPLACE_EXISTING);
            Path descEnPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName.substring(0, lastDot) + "_en.txt");
            Path descGrPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName.substring(0, lastDot) + "_gr.txt");
            if (!descriptionEn.isEmpty()) {
                Files.write(descEnPath, descriptionEn.getBytes());
            }
            if (!descriptionGr.isEmpty()) {
                Files.write(descGrPath, descriptionGr.getBytes());
            }
            String response = sendCommand("upload", fileName + ":" + descriptionEn + ":" + descriptionGr);
            if (response.equals("READY_FOR_PHOTO")) {
                FileInputStream fis = new FileInputStream(photoFile);
                byte[] buffer = new byte[(int) photoFile.length()];
                fis.read(buffer);
                fis.close();
                out.println(photoFile.length());
                response = in.readLine();
                if (!response.equals("START_SENDING")) {
                    System.out.println("Error: Server not ready to receive data: " + response);
                    return;
                }
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();
                System.out.println("Sent " + buffer.length + " bytes to server, waiting for response...");
                response = in.readLine();
                if (response.startsWith("SUCCESS:")) {
                    System.out.println(response.substring(8));
                    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date());
                    String formattedPost = "[" + timestamp + "] " + clientID + " posted " + fileName;
                    Path localProfilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
                    Files.write(localProfilePath, (formattedPost + System.lineSeparator()).getBytes(),
                            StandardOpenOption.APPEND);
                    System.out.println("Local profile updated.");
                } else if (response.startsWith("ERROR:")) {
                    System.out.println("Error uploading file: " + response.substring(6));
                } else {
                    System.out.println("Unexpected response from server: " + response);
                }
            } else {
                System.out.println("Server not ready to receive file: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error during upload: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void viewReposts() {
        System.out.println("\n===== My Reposts =====");
        try {
            Path othersPath = Paths.get(LOCAL_DATA_DIR, clientID, "Others_42" + clientID + ".txt");
            if (!Files.exists(othersPath) || Files.size(othersPath) == 0) {
                System.out.println("You have not reposted any content yet.");
                return;
            }
            List<String> lines = Files.readAllLines(othersPath);
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading reposts file: " + e.getMessage());
        }
    }
    private void updateLocalProfileWithComment(String serverResp) {
        if (serverResp == null || !serverResp.startsWith("COMMENT_POSTED:")) {
            return;
        }
        String formatted = serverResp.substring("COMMENT_POSTED:".length());
        appendToLocalProfile(formatted);
        updateLocalOthersWithNotification(formatted);
        System.out.println("Local profile updated with comment.");
    }
    private void appendToLocalProfile(String entry) {
        try {
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
            }
            Files.write(profilePath, (entry + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error updating local profile: " + e.getMessage());
        }
    }
    private void updateLocalOthersWithNotification(String notificationEntry) {
        try {
            Path othersPath = Paths.get(LOCAL_DATA_DIR, clientID, "Others_42" + clientID + ".txt");
            if (!Files.exists(othersPath)) {
                Files.createFile(othersPath);
            }
            Files.write(othersPath, (notificationEntry + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error updating local others file: " + e.getMessage());
        }
    }
    private void setLanguagePreference(Scanner scanner) {
        System.out.print("Choose language (en/gr): ");
        String lang = scanner.nextLine().trim().toLowerCase();
        if (!lang.equals("en") && !lang.equals("gr")) {
            System.out.println("Invalid language. Use 'en' or 'gr'.");
            return;
        }
        languagePreference = lang;
        String response = sendCommand("set_language", lang);
        System.out.println(response);
    }
    private void addLocalFollower(String followerID) {
        try {
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                Files.createFile(followersPath);
            }
            List<String> followers = new ArrayList<>();
            if (Files.size(followersPath) > 0) {
                followers = Files.readAllLines(followersPath);
            }
            if (!followers.contains(followerID)) {
                followers.add(followerID);
                Files.write(followersPath, followers);
                System.out.println("Local followers list updated.");
            }
        } catch (IOException e) {
            System.err.println("Error updating local followers file: " + e.getMessage());
        }
    }
    private void addLocalFollowing(String followedID) {
        try {
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                Files.createFile(followingPath);
            }
            List<String> following = new ArrayList<>();
            if (Files.size(followingPath) > 0) {
                following = Files.readAllLines(followingPath);
            }
            if (!following.contains(followedID)) {
                following.add(followedID);
                Files.write(followingPath, following);
                System.out.println("Local following list updated.");
            }
        } catch (IOException e) {
            System.err.println("Error updating local following file: " + e.getMessage());
        }
    }
    private void removeLocalFollower(String followerID) {
        try {
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                return;
            }
            List<String> followers = Files.readAllLines(followersPath);
            if (followers.remove(followerID)) {
                Files.write(followersPath, followers);
                System.out.println("Removed " + followerID + " from local followers list.");
            }
        } catch (IOException e) {
            System.err.println("Error updating local followers file: " + e.getMessage());
        }
    }
    private void removeLocalFollowing(String followingID) {
        try {
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                return;
            }
            List<String> following = Files.readAllLines(followingPath);
            if (following.remove(followingID)) {
                Files.write(followingPath, following);
                System.out.println("Removed " + followingID + " from local following list.");
            }
        } catch (IOException e) {
            System.err.println("Error updating local following file: " + e.getMessage());
        }
    }
    private void processNotificationsForFollows() {
        String response = sendCommand("get_notifications", "");
        if (response.equals("No notifications.")) {
            return;
        }
        String[] notifications = response.split("\\n");
        processFollowNotifications(notifications);
    }
    private void requestServerSync() {
        System.out.println("Requesting data synchronization from server...");
        String response = sendCommand("sync", clientID);
        System.out.println(response);
    }
    private void processFollowNotifications(String[] notifications) {
        boolean updatedFollowing = false;
        boolean updatedFollowers = false;
        for (String notification : notifications) {
            try {
                int timestampEnd = notification.indexOf("]") + 2; 
                if (timestampEnd <= 1) continue; 
                String messageContent = notification.substring(timestampEnd);
                if (messageContent.contains("accepted your follow request")) {
                    int endIndex = messageContent.indexOf(" accepted");
                    if (endIndex > 0) {
                        String acceptedBy = messageContent.substring(0, endIndex);
                        addLocalFollowing(acceptedBy);
                        updatedFollowing = true;
                        if (messageContent.contains("and is now following you")) {
                            addLocalFollower(acceptedBy);
                            updatedFollowers = true;
                        }
                    }
                }
                else if (messageContent.contains("is now following you") && !messageContent.contains("accepted")) {
                    int endIndex = messageContent.indexOf(" is now following you");
                    if (endIndex > 0) {
                        String follower = messageContent.substring(0, endIndex);
                        addLocalFollower(follower);
                        updatedFollowers = true;
                    }
                }
                else if (messageContent.contains("has unfollowed you")) {
                    int endIndex = messageContent.indexOf(" has unfollowed you");
                    if (endIndex > 0) {
                        String unfollower = messageContent.substring(0, endIndex);
                        removeLocalFollower(unfollower);
                        updatedFollowers = true;
                    }
                }
                else if (messageContent.contains("rejected your follow request")) {
                    int endIndex = messageContent.indexOf(" rejected your follow request");
                    if (endIndex > 0) {
                        String rejector = messageContent.substring(0, endIndex);
                        System.out.println("Your follow request to " + rejector + " was rejected");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing notification: " + notification);
                e.printStackTrace();
            }
        }
        if (updatedFollowing) {
            System.out.println("Updated local following list based on notifications.");
        }
        if (updatedFollowers) {
            System.out.println("Updated local followers list based on notifications.");
        }
    }
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String serverAddress = "localhost"; 
        int serverPort = 8000; 
        if (args.length >= 2) {
            serverAddress = args[0];
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8000.");
            }
        } else {
            System.out.print("Enter server address (or press Enter for localhost): ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                serverAddress = input;
            }
            System.out.print("Enter server port (or press Enter for 8000): ");
            input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                try {
                    serverPort = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number. Using default port 8000.");
                }
            }
        }
        System.out.println("Connecting to server at " + serverAddress + ":" + serverPort);
        SocialNetworkClient client = new SocialNetworkClient(serverAddress, serverPort);
        client.start();
    }
}