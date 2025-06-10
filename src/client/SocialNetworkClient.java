package client;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

/**
 * Simple client implementation for the social network application.
 * This client connects to the server and provides a basic interface for interaction.
 */
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


    /**
     * Constructor for the Social Network Client
     * @param serverAddress The address of the server to connect to
     * @param serverPort The port of the server
     */
    public SocialNetworkClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.running = false;
        this.loggedIn = false;
    }

    /**
     * Connects to the server
     * @return True if the connection was successful, false otherwise
     */
    public boolean connect() {
        try {
            // Connect to the server
            socket = new Socket(serverAddress, serverPort);

            // Set up input and output streams
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            running = true;
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Disconnects from the server
     */
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

            // First ensure src/client directory exists
            Path clientPath = Paths.get(CLIENT_FOLDER);
            if (!Files.exists(clientPath)) {
                Files.createDirectories(clientPath);
                System.out.println("Created client directory: " + clientPath.toAbsolutePath());
            }

            // Create the main localdata directory if it doesn't exist
            Path localDataPath = Paths.get(LOCAL_DATA_DIR);
            if (!Files.exists(localDataPath)) {
                Files.createDirectories(localDataPath);
                System.out.println("Created local data directory: " + localDataPath.toAbsolutePath());
            }

            // Create client-specific directory
            Path clientDataPath = Paths.get(LOCAL_DATA_DIR, clientID);
            if (!Files.exists(clientDataPath)) {
                Files.createDirectories(clientDataPath);
                System.out.println("Created client data directory: " + clientDataPath.toAbsolutePath());
            }

            // Create photos directory
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
                System.out.println("Created photos directory: " + photosPath.toAbsolutePath());
            }

            // Create profile file if it doesn't exist
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
                System.out.println("Created local profile file: " + profilePath.toAbsolutePath());
            }

            // Create followers.txt file if it doesn't exist
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                Files.createFile(followersPath);
                System.out.println("Created local followers file: " + followersPath.toAbsolutePath());
            }

            // Create following.txt file if it doesn't exist
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                Files.createFile(followingPath);
                System.out.println("Created local following file: " + followingPath.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error initializing local storage: " + e.getMessage());
        }
    }

    /**
     * Handles the login/signup process
     * @return True if login/signup was successful, false otherwise
     */
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
            // Login
            System.out.print("Enter your client ID: ");
            clientID = scanner.nextLine();

            // Send login command to server
            String response = sendCommand("login", clientID);
            System.out.println(response);

            if (response.startsWith("Welcome")) {
                loggedIn = true;
                // Initialize local storage after successful login
                initializeLocalStorage();

                // Add synchronization here after successful login
                requestServerSync();

                return true;
            } else {
                return false;
            }
        } else if (choice.equals("2")) {
            // Signup
            System.out.print("Enter a new client ID: ");
            clientID = scanner.nextLine();

            // Send signup command to server
            String response = sendCommand("signup", clientID);
            System.out.println(response);

            if (response.startsWith("Welcome")) {
                loggedIn = true;
                // Initialize local storage after successful signup
                initializeLocalStorage();

                // For new accounts, synchronization helps establish baseline data
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

    /**
     * Sends a command to the server
     * @param command The command to send
     * @param parameters The parameters for the command
     * @return The response from the server
     */
    public String sendCommand(String command, String parameters) {
        if (!running) {
            return "Error: Not connected to server";
        }

        try {
            // Send the command to the server
            out.println(command + ":" + parameters);

            // Special handling for get_notifications command to handle multiple lines
            if (command.equals("get_notifications")) {
                // Read the first line to determine if there are notifications
                String firstLine = in.readLine();

                // If there are no notifications, just return the first line
                if (firstLine.equals("No notifications.")) {
                    return firstLine;
                }

                StringBuilder fullResponse = new StringBuilder(firstLine);

                out.println("continue_reading"); // Signal server to send more data

                // Read additional lines until we get a signal or timeout
                String line;
                while ((line = in.readLine()) != null && !line.equals("END_OF_NOTIFICATIONS")) {
                    fullResponse.append("\n").append(line);
                }

                return fullResponse.toString();
            }
            // Special handling for access_profile command to handle multi-line profile content
            else if (command.equals("access_profile")) {
                // Read the first line of response
                String firstLine = in.readLine();

                // Check if it's a profile response or an error/denied message
                if (firstLine.startsWith("PROFILE_START")) {
                    StringBuilder profileContent = new StringBuilder(firstLine);

                    // Read profile content until PROFILE_END
                    String line;
                    while ((line = in.readLine()) != null && !line.equals("PROFILE_END")) {
                        profileContent.append("\n").append(line);
                    }

                    // Add the end marker
                    profileContent.append("\nPROFILE_END");

                    return profileContent.toString();
                } else {
                    // For error or denied messages, just return the single line
                    return firstLine;
                }
            }
            else {
                // For other commands, just read a single line
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

    /**
     * Displays help information
     */
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
    /**
     * Starts the client command loop
     */
    public void start() {
        if (!connect()) {
            System.err.println("Failed to connect to server");
            return;
        }

        // Handle login/signup
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
                case "1": // Post
                    System.out.print("Enter your message: ");
                    String message = scanner.nextLine();

                    // Send the post command to server
                    String response = sendCommand("post", message);
                    System.out.println(response);

                    // If post was successful, update local profile as well
                    if (response.startsWith("Post created successfully")) {
                        try {
                            // Get the timestamp from the server response
                            String formattedPost = response.substring(response.indexOf("with: ") + 6);

                            // Update local profile file
                            Path localProfilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
                            Files.write(localProfilePath, (formattedPost + System.lineSeparator()).getBytes(),
                                    StandardOpenOption.APPEND);

                            System.out.println("Local profile updated.");
                        } catch (IOException | StringIndexOutOfBoundsException e) {
                            System.err.println("Error updating local profile: " + e.getMessage());
                        }
                    }
                    break;




                case "2": // Follow
                    System.out.print("Enter the client ID you want to follow: ");
                    String targetClientID = scanner.nextLine();

                    // Don't allow following yourself
                    if (targetClientID.equals(clientID)) {
                        System.out.println("Error: You cannot follow yourself.");
                        break;
                    }

                    // Send follow request to server
                    response = sendCommand("follow_request", targetClientID);
                    System.out.println(response);
                    break;


                case "3": // Unfollow
                    // Display current following list to make it easier for the user
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

                    // Don't allow unfollowing yourself
                    if (unfollowID.equals(clientID)) {
                        System.out.println("Error: You cannot unfollow yourself.");
                        break;
                    }

                    // Send unfollow request to server
                    response = sendCommand("unfollow", unfollowID);
                    System.out.println(response);

                    // If unfollow was successful, update local following list
                    if (response.startsWith("You have unfollowed")) {
                        removeLocalFollowing(unfollowID);
                    }
                    break;

                case "4": // Upload photo
                    uploadPhoto(scanner);
                    break;
                case "5": // View notifications
                    response = sendCommand("get_notifications", "");
                    System.out.println("\n===== Your Notifications =====");

                    // If there are no notifications
                    if (response.equals("No notifications.")) {
                        System.out.println(response);
                        break;
                    }

                    // Display all notifications by splitting by newline
                    String[] notifications = response.split("\\n");

                    // Create a list to track which notifications are posts that can be reposted
                    List<Integer> postIndices = new ArrayList<>();
                    List<String> postSenders = new ArrayList<>();
                    List<String> postContents = new ArrayList<>();

                    // Display notifications and track posts or comment events
                    List<String> commentRequestSenders = new ArrayList<>();
                    List<String> commentRequestTexts = new ArrayList<>();

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
                            } else if (messageContent.contains("approved your comment:")) {
                                int idx = messageContent.indexOf(" approved your comment:");
                                String approver = messageContent.substring(0, idx);
                                String com = messageContent.substring(idx + " approved your comment:".length()).trim();
                                String resp = sendCommand("comment", approver + ":" + com);
                                System.out.println(resp.startsWith("COMMENT_POSTED:") ? resp.substring(15) : resp);
                                updateLocalProfileWithComment(approver, com, resp);
                            } else if (messageContent.contains("rejected your comment:")) {
                                int idx = messageContent.indexOf(" rejected your comment:");
                                String rejector = messageContent.substring(0, idx);
                                System.out.println("Your comment was rejected by " + rejector + ".");
                            }
                        }
                    }

                    // Process follow notifications to update local files
                    processFollowNotifications(notifications);

                    // Check if there are pending follow requests to respond to
                    boolean hasPendingRequests = false;
                    if (response.contains("follow request from")) {
                        hasPendingRequests = true;
                    }

                    // Offer options based on notification types
                    System.out.println("\nWhat would you like to do?");

                    int optionNum = 1;
                    int followOption = -1;
                    int repostOption = -1;
                    int commentOption = -1;
                    int commentReqOption = -1;

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

                    int returnOption = optionNum;
                    System.out.println(returnOption + ". Return to main menu");

                    System.out.print("> ");
                    choice = scanner.nextLine().trim();

                    // Process user choice
                    if (followOption != -1 && choice.equals(Integer.toString(followOption))) {
                        // Handle follow requests
                        System.out.println("\nPending follow requests:");
                        int requestCount = 0;
                        for (int i = 0; i < notifications.length; i++) {
                            if (notifications[i].contains("You have a follow request from")) {
                                requestCount++;
                                // Extract the sender ID
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

                        // Send response to server
                        response = sendCommand("follow_response", requestorID + ":" + responseChoice);
                        System.out.println(response);

                        // Update local files based on response
                        if (responseChoice.equals("1") || responseChoice.equals("2")) {
                            // They are now following us (in both cases)
                            addLocalFollower(requestorID);
                        }

                        if (responseChoice.equals("1")) {
                            // We're also following them (only in case 1)
                            addLocalFollowing(requestorID);
                        }
                    }
                    else if (repostOption != -1 && choice.equals(Integer.toString(repostOption))) {
                        // Handle repost
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

                        // Get the selected post
                        int selectedIndex = selection - 1;
                        String originalSender = postSenders.get(selectedIndex);
                        String originalContent = postContents.get(selectedIndex);

                        // Ask for a comment
                        System.out.print("Add a comment to your repost (optional, press Enter to skip): ");
                        String comment = scanner.nextLine().trim();

                        // Confirm repost
                        System.out.print("Repost this content? (y/n): ");
                        String confirm = scanner.nextLine().trim().toLowerCase();
                        if (!confirm.equals("y")) {
                            System.out.println("Repost cancelled.");
                            break;
                        }

                        // Send repost command to server
                        response = sendCommand("repost", originalSender + ":" + originalContent + ":" + comment);

                        if (response.startsWith("SUCCESS:")) {
                            System.out.println(response.substring(8));

                            // Create local Others directory if it doesn't exist
                            try {
                                // Path for the Others text file
                                Path othersPath = Paths.get(LOCAL_DATA_DIR, clientID, "Others_42" + clientID + ".txt");

                                // Create the file if it doesn't exist
                                if (!Files.exists(othersPath)) {
                                    Files.createFile(othersPath);
                                }

                                // Format the repost entry
                                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                        .format(new java.util.Date());
                                String formattedRepost = "[" + timestamp + "] REPOST from " + originalSender + ": " + originalContent;
                                if (!comment.isEmpty()) {
                                    formattedRepost += "\n[" + timestamp + "] COMMENT: " + comment;
                                }

                                // Append to file
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
                    break;

                case "6": // Access profile (previously 11)
                    accessProfile(scanner);
                    break;

                case "7": // View reposts (previously 12)
                    viewReposts();
                    break;

                case "8": // Search for a photo
                    searchPhoto(scanner);
                    break;

                case "9": // Set language
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
    }/**
     * Handles requesting access to another client's profile
     * @param scanner Scanner for user input
     */
    private void accessProfile(Scanner scanner) {
        System.out.println("\n===== Access Client Profile =====");

        // Display information about the feature
        System.out.println("This feature allows you to view the profile of another client that you follow.");
        System.out.println("The server will check if you are following the requested client in the social graph.");

        System.out.print("\nEnter the client ID whose profile you want to access: ");
        String targetID = scanner.nextLine().trim();

        // Don't allow accessing your own profile through this method
        if (targetID.equals(clientID)) {
            System.out.println("You can view your own profile locally at: " +
                    Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID));
            return;
        }

        System.out.println("Sending profile access request to server...");

        // Send the request to the server
        String response = sendCommand("access_profile", targetID);

        // Process the response
        if (response.startsWith("PROFILE_START")) {
            // Extract the profile content
            String profileContent = response.substring("PROFILE_START".length(),
                    response.length() - "PROFILE_END".length());

            System.out.println("\n===== Profile of Client " + targetID + " =====");
            System.out.println(profileContent);
            System.out.println("=============================");
        } else if (response.startsWith("DENIED:")) {
            // Access was denied
            System.out.println("\n" + response.substring("DENIED:".length()));
            System.out.println("According to the server's social graph, you must follow this client to access their profile.");
            System.out.println("Use option 3 in the main menu to send a follow request if you want to access this profile.");
        } else if (response.startsWith("ERROR:")) {
            // Error occurred
            System.out.println("\nError: " + response.substring("ERROR:".length()));
        } else {
            // Unknown response
            System.out.println("\nUnexpected response from server: " + response);
        }
    }

    /**
     * Handles searching for a photo in the social graph
     * @param scanner Scanner for user input
     */
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

        // Send search command to server
        String response = sendCommand("search", params);

        if (response.startsWith("RESULT:")) {
            String results = response.substring("RESULT:".length());
            System.out.println("\n===== Search Results =====");

            // Check if there are any search results
            if (results.contains("No matching photos found") || !results.contains("##ENTRIES##")) {
                System.out.println(results);
                return;
            }

            // Split the entries if we have the separator
            String[] parts = results.split("##ENTRIES##");
            System.out.println(parts[0]); // Print the header

            // Process the entries if there are any
            List<String> clientsWithPhoto = new ArrayList<>();
            if (parts.length > 1) {
                // Replace our custom newline markers and print entries
                String entries = parts[1].replace("##NEWLINE##", "\n");
                System.out.println(entries);

                // Extract client IDs from results
                String[] lines = entries.split("\n");
                for (String line : lines) {
                    if (line.contains("Client ID:")) {
                        // Extract client ID
                        int startIdx = line.indexOf("Client ID:") + 10;
                        int endIdx = line.indexOf(" - File:");
                        if (startIdx > 0 && endIdx > startIdx) {
                            String clientId = line.substring(startIdx, endIdx).trim();
                            clientsWithPhoto.add(clientId);
                        }
                    }
                }
            }

            // If we found clients with this photo, offer to download
            if (!clientsWithPhoto.isEmpty()) {
                System.out.println("\nWould you like to download this photo? (y/n)");
                String choice = scanner.nextLine().trim().toLowerCase();

                if (choice.equals("y")) {
                    // If multiple clients have the photo, choose one randomly
                    String selectedClient;
                    if (clientsWithPhoto.size() > 1) {
                        // Random selection as required
                        int randomIndex = new java.util.Random().nextInt(clientsWithPhoto.size());
                        selectedClient = clientsWithPhoto.get(randomIndex);
                        System.out.println("Randomly selected client " + selectedClient + " to download from.");
                    } else {
                        // Only one client has the photo
                        selectedClient = clientsWithPhoto.get(0);
                    }

                    // Start the download process
                    initiateDownload(fileName, selectedClient, scanner);
                }
            }
        } else if (response.startsWith("ERROR:")) {
            System.out.println("Error: " + response.substring(6));
        } else {
            System.out.println("Unexpected response from server: " + response);
        }
    }

    /**
     * Initiates the download process for a photo
     * @param fileName The name of the photo file to download
     * @param sourceClientID The ID of the client to download from
     * @param scanner Scanner for user input
     */
    private void initiateDownload(String fileName, String sourceClientID, Scanner scanner) {
        System.out.println("\nInitiating download of " + fileName + " from client " + sourceClientID + "...");

        // Make sure the local directories exist for storing the downloaded file
        try {
            // Create photos directory if it doesn't exist
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
                System.out.println("Created photos directory: " + photosPath);
            }
        } catch (IOException e) {
            System.err.println("Error creating directories: " + e.getMessage());
            return;
        }

        // Send the download request to the server
        // The format is: download:fileName:sourceClientID
        // This tells the server which file to download and from which client's directory
        String downloadRequest = fileName + ":" + sourceClientID;
        String response = sendCommand("download", downloadRequest);

        if (response.startsWith("ERROR:")) {
            System.out.println("Error initiating download: " + response.substring(6));
            return;
        } else if (response.equals("HANDSHAKE_INIT")) {
            // The server has initiated the 3-way handshake process
            System.out.println("Beginning 3-way handshake with server...");

            // Continue with the 3-way handshake process
            performHandshake(fileName, sourceClientID, scanner);
        } else {
            System.out.println("Unexpected response from server: " + response);
        }
    }
    private void performHandshake(String fileName, String sourceClientID, Scanner scanner) {
        try {
            // Step 1: Send SYN (synchronize) message
            System.out.println("Step 1/3: Sending connection request (SYN)...");
            String response = sendCommand("download_syn", clientID);

            if (!response.startsWith("SYN_ACK:")) {
                System.out.println("Error in handshake: Expected SYN_ACK, got: " + response);
                return;
            }

            // Step 2: Received SYN-ACK, extract sequence number if needed
            System.out.println("Step 2/3: Received connection acknowledgment (SYN-ACK)");
            String sequenceNumber = response.substring("SYN_ACK:".length());

            // Step 3: Send ACK with the file request
            System.out.println("Step 3/3: Sending acknowledgment with file request (ACK)...");
            response = sendCommand("download_ack", sequenceNumber + ":" + fileName + ":" + sourceClientID);

            if (response.equals("TRANSFER_READY")) {
                System.out.println("Handshake completed successfully. Starting file transfer...");

                // Continue with receiving the file
                receiveFile(fileName, scanner);
            } else {
                System.out.println("Error starting file transfer: " + response);
            }
        } catch (Exception e) {
            System.err.println("Error during handshake: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Receives a file from the server
     * @param fileName The name of the file being received
     * @param scanner Scanner for user input
     */
    /**
     * Receives a file from the server
     * @param fileName The name of the file being received
     * @param scanner Scanner for user input
     */
    private void receiveFile(String fileName, Scanner scanner) {
        try {
            System.out.println("Waiting for file transfer to begin...");

            // Receive the file info
            String fileInfo = in.readLine();
            if (!fileInfo.startsWith("FILE_INFO:")) {
                System.err.println("Error: Expected file info, got: " + fileInfo);
                return;
            }

            // Parse file info
            String[] parts = fileInfo.substring("FILE_INFO:".length()).split(":");
            int numChunks = Integer.parseInt(parts[0]);
            int fileSize = Integer.parseInt(parts[1]);

            System.out.println("Receiving file: " + fileName + " (" + fileSize + " bytes in " + numChunks + " chunks)");

            // Acknowledge file info
            out.println("FILE_INFO_ACK");

            // Prepare to receive chunks
            ByteArrayOutputStream photoData = new ByteArrayOutputStream();

            for (int i = 1; i <= numChunks; i++) {
                // Receive chunk header
                String chunkHeader = in.readLine();
                if (!chunkHeader.startsWith("CHUNK:")) {
                    System.err.println("Error: Expected chunk header, got: " + chunkHeader);
                    return;
                }

                // Parse chunk header
                parts = chunkHeader.substring("CHUNK:".length()).split(":");
                int chunkNumber = Integer.parseInt(parts[0]);
                int totalChunks = Integer.parseInt(parts[1]);
                int chunkSize = Integer.parseInt(parts[2]);

                // Receive chunk data
                String encodedData = in.readLine();

                // Specific behaviors for special chunks
                if (chunkNumber == 3) {
                    // Chunk 3: Don't send ACK for the first transmission
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Purposely not sending ACK");

                    // Wait for the server to timeout and resend the chunk (looking for the same header)
                    String resendHeader = in.readLine();
                    if (resendHeader == null || !resendHeader.startsWith("CHUNK:")) {
                        System.err.println("Error: Expected resent chunk, got: " + resendHeader);
                        return;
                    }

                    // Parse the resent chunk header
                    parts = resendHeader.substring("CHUNK:".length()).split(":");
                    int resendChunkNumber = Integer.parseInt(parts[0]);

                    if (resendChunkNumber != chunkNumber) {
                        System.err.println("Error: Expected resent chunk " + chunkNumber +
                                ", got chunk " + resendChunkNumber);
                        return;
                    }

                    // Read the resent data
                    String resendData = in.readLine();

                    System.out.println("Received resent chunk " + chunkNumber + "/" + totalChunks +
                            " - Now sending ACK");

                    // Send ACK for the resent chunk
                    out.println("CHUNK_ACK:" + chunkNumber);

                    // Use the resent data
                    encodedData = resendData;
                }
                else if (chunkNumber == 6) {
                    // Chunk 6: Delay the ACK
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Delaying ACK");

                    // Delay sending the ACK
                    try {
                        Thread.sleep(3000); // 3-second delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    System.out.println("Sending delayed ACK for chunk " + chunkNumber);
                    out.println("CHUNK_ACK:" + chunkNumber);

                    // Send a duplicate ACK to test server's handling
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    System.out.println("Sending duplicate ACK for chunk " + chunkNumber + " to test server handling");
                    out.println("CHUNK_ACK:" + chunkNumber);
                }
                else {
                    // Normal case for all other chunks
                    System.out.println("Received chunk " + chunkNumber + "/" + totalChunks +
                            " (" + chunkSize + " bytes) - Sending ACK");
                    out.println("CHUNK_ACK:" + chunkNumber);
                }

                // Decode and store the chunk data
                byte[] chunkData = Base64.getDecoder().decode(encodedData);
                photoData.write(chunkData);
            }

            // Process the description or no-description message
            String descriptionInfo = in.readLine();
            String description = null;

            if (descriptionInfo.startsWith("DESCRIPTION:")) {
                // Parse description size
                int descriptionSize = Integer.parseInt(descriptionInfo.substring("DESCRIPTION:".length()));
                System.out.println("Receiving description (" + descriptionSize + " bytes)");

                // Acknowledge description info
                out.println("DESCRIPTION_ACK");

                // Receive the description content
                description = in.readLine();

                // Acknowledge receipt
                out.println("DESCRIPTION_RECEIVED");

                System.out.println("Description received: " + description);
            }
            else if (descriptionInfo.equals("NO_DESCRIPTION")) {
                System.out.println("No description available for this photo");

                // Acknowledge the no-description message
                out.println("NO_DESCRIPTION_ACK");
            }
            else {
                System.err.println("Error: Expected description info, got: " + descriptionInfo);
                return;
            }

            // Wait for transfer complete message
            String completeMsg = in.readLine();
            if (!completeMsg.equals("TRANSFER_COMPLETE")) {
                System.err.println("Error: Expected transfer complete message, got: " + completeMsg);
                return;
            }

            System.out.println("The transmission is completed.");

            // Save the received file and description locally
            Path photosDir = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
            }

            // Save the photo
            Path photoPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName);
            Files.write(photoPath, photoData.toByteArray());

            // Save the description if available
            if (description != null) {
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                Path descPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", baseName + "_" + languagePreference + ".txt");
                Files.write(descPath, description.getBytes("UTF-8"));
            }

            // Update the local profile to reflect the download
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String formattedMessage = "[" + timestamp + "] Downloaded " + fileName;

            // Get the profile file path
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);

            // Append the message to the profile file
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

            // Validate the file exists and is a valid photo
            File photoFile = new File(photoPath);
            if (!photoFile.exists() || !photoFile.isFile()) {
                System.out.println("Error: File does not exist.");
                return;
            }

            // Check if file has a valid image extension
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

            // Create photos directory if it doesn't exist
            Path photosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");
            if (!Files.exists(photosPath)) {
                Files.createDirectories(photosPath);
            }

            // Copy the photo to the local photos directory
            Path destPhotoPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", photoFile.getName());
            Files.copy(photoFile.toPath(), destPhotoPath, StandardCopyOption.REPLACE_EXISTING);

            // Create the description files locally
            Path descEnPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName.substring(0, lastDot) + "_en.txt");
            Path descGrPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos", fileName.substring(0, lastDot) + "_gr.txt");
            if (!descriptionEn.isEmpty()) {
                Files.write(descEnPath, descriptionEn.getBytes());
            }
            if (!descriptionGr.isEmpty()) {
                Files.write(descGrPath, descriptionGr.getBytes());
            }

            // Send the upload command to server
            String response = sendCommand("upload", fileName + ":" + descriptionEn + ":" + descriptionGr);

            // Check if server is ready to receive the file
            if (response.equals("READY_FOR_PHOTO")) {
                // Open file input stream for the photo
                FileInputStream fis = new FileInputStream(photoFile);
                byte[] buffer = new byte[(int) photoFile.length()];
                fis.read(buffer);
                fis.close();

                // Send the file size first
                out.println(photoFile.length());

                // Wait for server to confirm it's ready for data
                response = in.readLine();
                if (!response.equals("START_SENDING")) {
                    System.out.println("Error: Server not ready to receive data: " + response);
                    return;
                }

                // Send the file data
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();

                System.out.println("Sent " + buffer.length + " bytes to server, waiting for response...");

                // Wait for server response
                response = in.readLine();

                if (response.startsWith("SUCCESS:")) {
                    System.out.println(response.substring(8));

                    // Update local profile
                    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date());
                    String formattedPost = "[" + timestamp + "] " + clientID + " posted " + fileName;

                    // Update the client's local profile file
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

    private void updateLocalProfileWithComment(String target, String comment, String serverResp) {
        if (serverResp == null || !serverResp.startsWith("COMMENT_POSTED:")) {
            return;
        }
        String formatted = serverResp.substring("COMMENT_POSTED:".length());
        try {
            Path profilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_42" + clientID);
            Files.write(profilePath, (formatted + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);
            System.out.println("Local profile updated with comment.");
        } catch (IOException e) {
            System.err.println("Error updating local profile: " + e.getMessage());
        }
    }

    /**
     * Allows the user to set their preferred language for photo descriptions.
     */
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
            // Create the followers file if it doesn't exist
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                Files.createFile(followersPath);
            }

            // Read existing followers
            List<String> followers = new ArrayList<>();
            if (Files.size(followersPath) > 0) {
                followers = Files.readAllLines(followersPath);
            }

            // Add the new follower if not already in the list
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
            // Create the following file if it doesn't exist
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                Files.createFile(followingPath);
            }

            // Read existing following list
            List<String> following = new ArrayList<>();
            if (Files.size(followingPath) > 0) {
                following = Files.readAllLines(followingPath);
            }

            // Add the new following if not already in the list
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
            // Check if the followers file exists
            Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
            if (!Files.exists(followersPath)) {
                return;
            }

            // Read existing followers
            List<String> followers = Files.readAllLines(followersPath);

            // Remove the follower
            if (followers.remove(followerID)) {
                Files.write(followersPath, followers);
                System.out.println("Removed " + followerID + " from local followers list.");
            }
        } catch (IOException e) {
            System.err.println("Error updating local followers file: " + e.getMessage());
        }
    }

    /**
     * Removes a client from the local following file
     * @param followingID The ID of the client to unfollow
     */
    private void removeLocalFollowing(String followingID) {
        try {
            // Check if the following file exists
            Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
            if (!Files.exists(followingPath)) {
                return;
            }

            // Read existing following list
            List<String> following = Files.readAllLines(followingPath);

            // Remove the following
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

        // If there are no notifications, return
        if (response.equals("No notifications.")) {
            return;
        }

        // Split the notifications by new line
        String[] notifications = response.split("\\n");

        // Process the notifications
        processFollowNotifications(notifications);
    }

    private void requestServerSync() {
        System.out.println("Requesting data synchronization from server...");
        String response = sendCommand("sync", clientID);
        System.out.println(response);
    }

    private void processFollowNotifications(String[] notifications) {
        // Track if we've made updates for logging
        boolean updatedFollowing = false;
        boolean updatedFollowers = false;

        for (String notification : notifications) {
            try {
                // Extract the timestamp to skip it when parsing
                int timestampEnd = notification.indexOf("]") + 2; // Skip the timestamp part "[yyyy-MM-dd HH:mm:ss] "

                if (timestampEnd <= 1) continue; // Invalid format

                String messageContent = notification.substring(timestampEnd);

                // Case 1: Someone accepted our follow request (with or without following back)
                if (messageContent.contains("accepted your follow request")) {
                    // Extract the username that accepted our request (it's at the beginning of the message)
                    int endIndex = messageContent.indexOf(" accepted");
                    if (endIndex > 0) {
                        String acceptedBy = messageContent.substring(0, endIndex);

                        // Update our local following list - we are now following them
                        addLocalFollowing(acceptedBy);
                        updatedFollowing = true;

                        // If they also followed back, update our followers list
                        if (messageContent.contains("and is now following you")) {
                            addLocalFollower(acceptedBy);
                            updatedFollowers = true;
                        }
                    }
                }
                // Case 2: Someone followed us (without us following them first)
                else if (messageContent.contains("is now following you") && !messageContent.contains("accepted")) {
                    int endIndex = messageContent.indexOf(" is now following you");
                    if (endIndex > 0) {
                        String follower = messageContent.substring(0, endIndex);
                        addLocalFollower(follower);
                        updatedFollowers = true;
                    }
                }
                // Case 3: Someone unfollowed us
                else if (messageContent.contains("has unfollowed you")) {
                    int endIndex = messageContent.indexOf(" has unfollowed you");
                    if (endIndex > 0) {
                        String unfollower = messageContent.substring(0, endIndex);
                        removeLocalFollower(unfollower);
                        updatedFollowers = true;
                    }
                }
                // Case 4: We got rejected
                else if (messageContent.contains("rejected your follow request")) {
                    int endIndex = messageContent.indexOf(" rejected your follow request");
                    if (endIndex > 0) {
                        String rejector = messageContent.substring(0, endIndex);
                        System.out.println("Your follow request to " + rejector + " was rejected");
                        // No need to update local files here
                    }
                }
            } catch (Exception e) {
                // If there's any error parsing a notification, just continue to the next one
                System.err.println("Error processing notification: " + notification);
                e.printStackTrace();
            }
        }

        // Log updates if made
        if (updatedFollowing) {
            System.out.println("Updated local following list based on notifications.");
        }
        if (updatedFollowers) {
            System.out.println("Updated local followers list based on notifications.");
        }
    }
    /**
     * Main method to start the client
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String serverAddress = "localhost"; // Default
        int serverPort = 8000; // Default

        // If command line arguments are provided, use them
        if (args.length >= 2) {
            serverAddress = args[0];
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8000.");
            }
        } else {
            // Prompt user for server address if not provided
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