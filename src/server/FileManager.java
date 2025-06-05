package server;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages file operations for the social network server,
 * including profile updates, file searches, and synchronization.
 */
public class FileManager {
    public static final Logger logger = Logger.getLogger(FileManager.class.getName());
    public static final String SRC_FOLDER = "src";
    public static final String DATA_FOLDER = SRC_FOLDER + File.separator + "data";
    public static final String SOCIAL_GRAPH_FILENAME = "SocialGraph.txt";

    /**
     * Initializes user files and directories
     * @param clientID The ID of the client
     * @return true if successful, false otherwise
     */
    public boolean initializeClientFiles(String clientID) {
        try {
            // Create client directory in data folder
            Path clientDir = Paths.get(DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
                logger.info("Created client directory: " + clientDir.toAbsolutePath());
            }

            // Create client photos directory
            Path photosDir = Paths.get(DATA_FOLDER, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
                logger.info("Created photos directory for client " + clientID);
            }

            // Create profile file
            Path profilePath = Paths.get(DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
                logger.info("Created profile file for client " + clientID);

            }

            // Add user to SocialGraph.txt
            addClientToSocialGraph(clientID);

            return true;
        } catch (IOException e) {
            logger.severe("Error initializing client files for " + clientID + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a client to the social graph file
     * @param clientID The ID of the client to add
     * @throws IOException If there is an error updating the file
     */
    private void addClientToSocialGraph(String clientID) throws IOException {
        Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);

        // Read all lines from the social graph file
        List<String> lines = Files.readAllLines(socialGraphPath);

        // Check if the client is already in the social graph
        boolean clientExists = false;
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0 && parts[0].equals(clientID)) {
                clientExists = true;
                break;
            }
        }

        // If the client is not in the social graph, add them
        if (!clientExists) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(socialGraphPath.toFile(), true))) {
                writer.println(clientID);
            }
            logger.info("Added client " + clientID + " to social graph");
        }
    }

    /**
     * Checks if a client exists
     * @param clientID The ID of the client to check
     * @return true if the client exists, false otherwise
     */
    public boolean clientExists(String clientID) {
        Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);

        try {
            if (!Files.exists(socialGraphPath)) {
                return false;
            }

            List<String> lines = Files.readAllLines(socialGraphPath);
            for (String line : lines) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0 && parts[0].equals(clientID)) {
                    return true;
                }
            }

            return false;
        } catch (IOException e) {
            logger.severe("Error checking if client exists: " + e.getMessage());
            return false;
        }
    }


    /**
     * Creates a follow relationship between two clients in the SocialGraph.txt file
     * @param followerID The ID of the client who is following
     * @param followedID The ID of the client being followed
     * @return True if successful, false otherwise
     */
    public boolean createFollowRelationship(String followerID, String followedID) {
        try {
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);

            // Read all lines from the social graph file
            List<String> lines = Files.readAllLines(socialGraphPath);
            boolean followedFound = false;

            // Update the existing followed client's line if found
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\s+");

                if (parts.length > 0 && parts[0].equals(followedID)) {
                    followedFound = true;

                    // Check if follower already exists
                    boolean followerExists = false;
                    for (int j = 1; j < parts.length; j++) {
                        if (parts[j].equals(followerID)) {
                            followerExists = true;
                            break;
                        }
                    }

                    // Add follower if not already in the list
                    if (!followerExists) {
                        lines.set(i, line + " " + followerID);
                    }

                    break;
                }
            }

            // If followed client not found, add a new line
            if (!followedFound) {
                lines.add(followedID + " " + followerID);
            }

            // Write the updated lines back to the file
            Files.write(socialGraphPath, lines);

            logger.info("Created follow relationship: " + followerID + " follows " + followedID);
            return true;
        } catch (IOException e) {
            logger.severe("Error creating follow relationship: " + e.getMessage());
            return false;
        }
    }
    /**
     * Removes a follow relationship from the SocialGraph.txt file
     * @param followerID The ID of the client who is unfollowing
     * @param followedID The ID of the client being unfollowed
     * @return True if successful, false otherwise
     */
    public boolean removeFollowRelationship(String followerID, String followedID) {
        try {
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);

            // Read all lines from the social graph file
            List<String> lines = Files.readAllLines(socialGraphPath);

            // Find the followed client's line
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\s+");

                if (parts.length > 0 && parts[0].equals(followedID)) {
                    // Remove the follower
                    StringBuilder newLine = new StringBuilder(followedID);
                    boolean removed = false;

                    for (int j = 1; j < parts.length; j++) {
                        if (!parts[j].equals(followerID)) {
                            newLine.append(" ").append(parts[j]);
                        } else {
                            removed = true;
                        }
                    }

                    // Update the line if follower was removed
                    if (removed) {
                        lines.set(i, newLine.toString());
                        Files.write(socialGraphPath, lines);
                        logger.info("Removed follow relationship: " + followerID + " unfollowed " + followedID);
                        return true;
                    }

                    break;
                }
            }

            logger.warning("Follow relationship not found: " + followerID + " -> " + followedID);
            return false;
        } catch (IOException e) {
            logger.severe("Error removing follow relationship: " + e.getMessage());
            return false;
        }
    }

    public boolean isFollowing(String followerID, String followedID) {
        try {
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);

            if (!Files.exists(socialGraphPath)) {
                return false;
            }

            List<String> lines = Files.readAllLines(socialGraphPath);

            // Look for the line with the followed client
            for (String line : lines) {
                String[] parts = line.split("\\s+");

                // The first part is the client ID being followed
                if (parts.length > 0 && parts[0].equals(followedID)) {
                    // Check if follower is in the list
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].equals(followerID)) {
                            return true;
                        }
                    }
                    // Follower not found in this line
                    return false;
                }
            }

            // Followed client not found in social graph
            return false;
        } catch (IOException e) {
            logger.severe("Error checking follow relationship: " + e.getMessage());
            return false;
        }
    }


}