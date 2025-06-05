package server;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static server.FileManager.SOCIAL_GRAPH_FILENAME;

/**
 * ClientServerSynchronizer
 */
public class ClientServerSynchronizer {

    private static final String SRC_FOLDER = "src";
    private static final String DATA_FOLDER = SRC_FOLDER + File.separator + "data";
    private static final String CLIENT_FOLDER = SRC_FOLDER + File.separator + "client";
    private static final String LOCAL_DATA_DIR = CLIENT_FOLDER + File.separator + "localdata";
    private static final int TEAM_NUMBER = 42;

    /**
     * Synchronizes a client's local data with the server data
     * @param clientID The ID of the client
     * @return True if synchronization was successful, false otherwise
     */
    public static boolean synchronizeClientData(String clientID) {
        try {
            System.out.println("Starting data synchronization for client " + clientID);

            createLocalDirectories(clientID);

            synchronizeProfileFile(clientID);

            synchronizePhotosDirectory(clientID);

            synchronizeSocialGraph(clientID);

            synchronizeRepostsFile(clientID);

            System.out.println("Synchronization completed successfully for client " + clientID);
            return true;
        } catch (Exception e) {
            System.err.println("Error during synchronization for client " + clientID + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates the necessary local directories for the client if they don't exist
     * @param clientID The ID of the client
     * @throws IOException If an error occurs creating directories
     */
    private static void createLocalDirectories(String clientID) throws IOException {
        // Create client directory
        Path clientPath = Paths.get(CLIENT_FOLDER);
        if (!Files.exists(clientPath)) {
            Files.createDirectories(clientPath);
            System.out.println("Created client directory: " + clientPath.toAbsolutePath());
        }

        // Create local data directory
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
    }

    /**
     * Synchronizes the client's profile file with the server
     * @param clientID The ID of the client
     * @throws IOException If an error occurs reading or writing files
     */
    private static void synchronizeProfileFile(String clientID) throws IOException {
        Path serverProfilePath = Paths.get(DATA_FOLDER, clientID, "Profile_" + TEAM_NUMBER + clientID);
        Path localProfilePath = Paths.get(LOCAL_DATA_DIR, clientID, "Profile_" + TEAM_NUMBER + clientID);

        // Check if server profile exists
        if (!Files.exists(serverProfilePath)) {
            System.out.println("Server profile doesn't exist for client " + clientID + ". Nothing to synchronize.");
            return;
        }

        if (!Files.exists(localProfilePath)) {
            Files.copy(serverProfilePath, localProfilePath);
            System.out.println("Created local profile from server for client " + clientID);
        } else {
            List<String> serverLines = Files.readAllLines(serverProfilePath);
            List<String> localLines = Files.readAllLines(localProfilePath);

            if (serverLines.size() > localLines.size()) {
                Files.copy(serverProfilePath, localProfilePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Updated local profile from server for client " + clientID +
                        " (server had " + serverLines.size() + " lines, local had " +
                        localLines.size() + " lines)");
            } else if (localLines.size() > serverLines.size()) {
                Files.copy(localProfilePath, serverProfilePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Updated server profile from local for client " + clientID +
                        " (local had " + localLines.size() + " lines, server had " +
                        serverLines.size() + " lines)");
            } else {
                if (!fileContentsEqual(serverLines, localLines)) {
                    mergeSocialContentByTimestamp(serverProfilePath, localProfilePath);
                    System.out.println("Merged profile content for client " + clientID);
                } else {
                    System.out.println("Profile files are already synchronized for client " + clientID);
                }
            }
        }
    }

    /**
     * Synchronizes the client's photos directory with the server
     * @param clientID The ID of the client
     * @throws IOException If an error occurs reading or writing files
     */
    private static void synchronizePhotosDirectory(String clientID) throws IOException {
        Path serverPhotosPath = Paths.get(DATA_FOLDER, clientID, "photos");
        Path localPhotosPath = Paths.get(LOCAL_DATA_DIR, clientID, "photos");

        if (!Files.exists(serverPhotosPath)) {
            Files.createDirectories(serverPhotosPath);
        }

        if (!Files.exists(localPhotosPath)) {
            Files.createDirectories(localPhotosPath);
        }

        try (DirectoryStream<Path> serverStream = Files.newDirectoryStream(serverPhotosPath)) {
            for (Path serverFile : serverStream) {
                Path localFile = localPhotosPath.resolve(serverFile.getFileName());

                if (!Files.exists(localFile)) {
                    Files.copy(serverFile, localFile);
                    System.out.println("Copied file from server to local: " + serverFile.getFileName());
                } else if (Files.getLastModifiedTime(serverFile).compareTo(
                        Files.getLastModifiedTime(localFile)) > 0) {
                    Files.copy(serverFile, localFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Updated local file from server (newer): " + serverFile.getFileName());
                }
            }
        }

        try (DirectoryStream<Path> localStream = Files.newDirectoryStream(localPhotosPath)) {
            for (Path localFile : localStream) {
                Path serverFile = serverPhotosPath.resolve(localFile.getFileName());

                if (!Files.exists(serverFile)) {
                    Files.copy(localFile, serverFile);
                    System.out.println("Copied file from local to server: " + localFile.getFileName());
                } else if (Files.getLastModifiedTime(localFile).compareTo(
                        Files.getLastModifiedTime(serverFile)) > 0) {
                    Files.copy(localFile, serverFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Updated server file from local (newer): " + localFile.getFileName());
                }
            }
        }

        System.out.println("Photos directory synchronized for client " + clientID);
    }

    /**
     * Synchronizes the client's social graph information (followers/following)
     * @param clientID The ID of the client
     * @throws IOException If an error occurs reading or writing files
     */
    private static void synchronizeSocialGraph(String clientID) throws IOException {
        Path socialGraphPath = Paths.get(DATA_FOLDER, SOCIAL_GRAPH_FILENAME);
        if (!Files.exists(socialGraphPath)) {
            System.out.println("Social graph file not found. Cannot synchronize social relationships.");
            return;
        }

        List<String> followers = new ArrayList<>();
        List<String> following = new ArrayList<>();

        List<String> lines = Files.readAllLines(socialGraphPath);

        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0) {
                String user = parts[0];

                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].equals(clientID)) {
                        following.add(user);
                        break;
                    }
                }

                if (user.equals(clientID)) {
                    for (int i = 1; i < parts.length; i++) {
                        followers.add(parts[i]);
                    }
                }
            }
        }

        Path followersPath = Paths.get(LOCAL_DATA_DIR, clientID, "followers.txt");
        if (!Files.exists(followersPath)) {
            Files.createFile(followersPath);
        }
        Files.write(followersPath, followers);
        System.out.println("Updated followers list for client " + clientID + " (" + followers.size() + " followers)");

        Path followingPath = Paths.get(LOCAL_DATA_DIR, clientID, "following.txt");
        if (!Files.exists(followingPath)) {
            Files.createFile(followingPath);
        }
        Files.write(followingPath, following);
        System.out.println("Updated following list for client " + clientID + " (following " + following.size() + " users)");
    }

    /**
     * Synchronizes the client's reposts file (Others_XclientID)
     * @param clientID The ID of the client
     * @throws IOException If an error occurs reading or writing files
     */
    private static void synchronizeRepostsFile(String clientID) throws IOException {
        Path serverOthersPath = Paths.get(DATA_FOLDER, clientID, "Others_" + TEAM_NUMBER + clientID + ".txt");
        Path localOthersPath = Paths.get(LOCAL_DATA_DIR, clientID, "Others_" + TEAM_NUMBER + clientID + ".txt");

        // Check if either file exists
        boolean serverExists = Files.exists(serverOthersPath);
        boolean localExists = Files.exists(localOthersPath);

        if (!serverExists && !localExists) {
            Files.createFile(serverOthersPath);
            Files.createFile(localOthersPath);
            System.out.println("Created empty Others files for client " + clientID);
        } else if (serverExists && !localExists) {
            Files.copy(serverOthersPath, localOthersPath);
            System.out.println("Copied Others file from server to local for client " + clientID);
        } else if (!serverExists && localExists) {
            Files.copy(localOthersPath, serverOthersPath);
            System.out.println("Copied Others file from local to server for client " + clientID);
        } else {
            List<String> serverLines = Files.readAllLines(serverOthersPath);
            List<String> localLines = Files.readAllLines(localOthersPath);

            if (serverLines.size() > localLines.size()) {
                Files.copy(serverOthersPath, localOthersPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Updated local Others file from server for client " + clientID);
            } else if (localLines.size() > serverLines.size()) {
                Files.copy(localOthersPath, serverOthersPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Updated server Others file from local for client " + clientID);
            } else {
                if (!fileContentsEqual(serverLines, localLines)) {
                    mergeSocialContentByTimestamp(serverOthersPath, localOthersPath);
                    System.out.println("Merged Others file content for client " + clientID);
                } else {
                    System.out.println("Others files are already synchronized for client " + clientID);
                }
            }
        }
    }

    /**
     * Checks if two files have identical content
     * @param file1Lines Lines from the first file
     * @param file2Lines Lines from the second file
     * @return True if the content is identical, false otherwise
     */
    private static boolean fileContentsEqual(List<String> file1Lines, List<String> file2Lines) {
        if (file1Lines.size() != file2Lines.size()) {
            return false;
        }

        for (int i = 0; i < file1Lines.size(); i++) {
            if (!file1Lines.get(i).equals(file2Lines.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Merges social content from two files based on timestamps
     * @param file1Path Path to the first file
     * @param file2Path Path to the second file
     * @throws IOException If an error occurs reading or writing files
     */
    private static void mergeSocialContentByTimestamp(Path file1Path, Path file2Path) throws IOException {
        List<String> file1Lines = Files.readAllLines(file1Path);
        List<String> file2Lines = Files.readAllLines(file2Path);

        Set<String> entriesSet = new LinkedHashSet<>();
        List<String> mergedLines = new ArrayList<>();

        Map<Long, String> timestampMap = new TreeMap<>();

        processFileLines(file1Lines, timestampMap);

        processFileLines(file2Lines, timestampMap);

        for (Map.Entry<Long, String> entry : timestampMap.entrySet()) {
            if (entriesSet.add(entry.getValue())) {
                mergedLines.add(entry.getValue());
            }
        }

        Files.write(file1Path, mergedLines);
        Files.write(file2Path, mergedLines);
    }

    /**
     * Processes lines from a file and adds them to a timestamp-sorted map
     * @param lines Lines from the file
     * @param timestampMap Map to store entries sorted by timestamp
     */
    private static void processFileLines(List<String> lines, Map<Long, String> timestampMap) {
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            if (line.startsWith("[") && line.contains("]")) {
                try {
                    int closeBracketIndex = line.indexOf("]");
                    String timestampStr = line.substring(1, closeBracketIndex).trim();

                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    java.util.Date date = sdf.parse(timestampStr);
                    long timestamp = date.getTime();

                    timestampMap.put(timestamp, line);
                } catch (Exception e) {
                    long randomTimestamp = System.currentTimeMillis() +
                            (long)(Math.random() * 1000);
                    timestampMap.put(randomTimestamp, line);
                }
            } else {

                long randomTimestamp = System.currentTimeMillis() +
                        (long)(Math.random() * 1000);
                timestampMap.put(randomTimestamp, line);
            }
        }
    }

    /**
     * Main method to test the synchronizer
     * @param args Command-line arguments (first arg = clientID)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ClientServerSynchronizer <clientID>");
            return;
        }

        String clientID = args[0];
        boolean success = synchronizeClientData(clientID);

        if (success) {
            System.out.println("Successfully synchronized data for client " + clientID);
        } else {
            System.out.println("Failed to synchronize data for client " + clientID);
        }
    }
}