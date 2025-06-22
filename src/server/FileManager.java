package server;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;
// Manages file storage and social graph operations.
public class FileManager {
    public static final Logger logger = Logger.getLogger(FileManager.class.getName());
    public static final String SRC_FOLDER = "src";
    public static final String DATA_FOLDER = SRC_FOLDER + File.separator + "data";
    public static final String SOCIAL_GRAPH_FILENAME = "SocialGraph.txt";
    public boolean initializeClientFiles(String clientID) {
        try {
            Path clientDir = Paths.get(DATA_FOLDER, clientID);
            if (!Files.exists(clientDir)) {
                Files.createDirectories(clientDir);
                logger.info("Created client directory: " + clientDir.toAbsolutePath());
            }
            Path photosDir = Paths.get(DATA_FOLDER, clientID, "photos");
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
                logger.info("Created photos directory for client " + clientID);
            }
            Path profilePath = Paths.get(DATA_FOLDER, clientID, "Profile_42" + clientID);
            if (!Files.exists(profilePath)) {
                Files.createFile(profilePath);
                logger.info("Created profile file for client " + clientID);
            }
            addClientToSocialGraph(clientID);
            return true;
        } catch (IOException e) {
            logger.severe("Error initializing client files for " + clientID + ": " + e.getMessage());
            return false;
        }
    }
    private void addClientToSocialGraph(String clientID) throws IOException {
        Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
        List<String> lines = Files.readAllLines(socialGraphPath);
        boolean clientExists = false;
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0 && parts[0].equals(clientID)) {
                clientExists = true;
                break;
            }
        }
        if (!clientExists) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(socialGraphPath.toFile(), true))) {
                writer.println(clientID);
            }
            logger.info("Added client " + clientID + " to social graph");
        }
    }
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
    public boolean createFollowRelationship(String followerID, String followedID) {
        try {
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
            List<String> lines = Files.readAllLines(socialGraphPath);
            boolean followedFound = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\s+");
                if (parts.length > 0 && parts[0].equals(followedID)) {
                    followedFound = true;
                    boolean followerExists = false;
                    for (int j = 1; j < parts.length; j++) {
                        if (parts[j].equals(followerID)) {
                            followerExists = true;
                            break;
                        }
                    }
                    if (!followerExists) {
                        lines.set(i, line + " " + followerID);
                    }
                    break;
                }
            }
            if (!followedFound) {
                lines.add(followedID + " " + followerID);
            }
            Files.write(socialGraphPath, lines);
            logger.info("Created follow relationship: " + followerID + " follows " + followedID);
            return true;
        } catch (IOException e) {
            logger.severe("Error creating follow relationship: " + e.getMessage());
            return false;
        }
    }
    public boolean removeFollowRelationship(String followerID, String followedID) {
        try {
            Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
            List<String> lines = Files.readAllLines(socialGraphPath);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\s+");
                if (parts.length > 0 && parts[0].equals(followedID)) {
                    StringBuilder newLine = new StringBuilder(followedID);
                    boolean removed = false;
                    for (int j = 1; j < parts.length; j++) {
                        if (!parts[j].equals(followerID)) {
                            newLine.append(" ").append(parts[j]);
                        } else {
                            removed = true;
                        }
                    }
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
            for (String line : lines) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0 && parts[0].equals(followedID)) {
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].equals(followerID)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            return false;
        } catch (IOException e) {
            logger.severe("Error checking follow relationship: " + e.getMessage());
            return false;
        }
    }
}