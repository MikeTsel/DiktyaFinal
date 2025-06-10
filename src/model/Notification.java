package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Class to represent a notification in the social network
 */
public class Notification {
    private String senderID;
    private String receiverID;
    private String type;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;
    private String status; // For follow requests: "pending", "accepted", "rejected"

    /**
     * Constructor for Notification
     * @param senderID The ID of the client sending the notification
     * @param receiverID The ID of the client receiving the notification
     * @param type The type of notification (e.g., "follow_request", "system")
     * @param content The content of the notification
     */
    public Notification(String senderID, String receiverID, String type, String content) {
        this.senderID = senderID;
        this.receiverID = receiverID;
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;

        // If it's a follow or photo request, set status to pending
        if (type.equals("follow_request") || type.equals("photo_request")) {
            this.status = "pending";
        } else {
            this.status = "none";
        }
    }

    /**
     * Gets the sender ID
     * @return The sender ID
     */
    public String getSenderID() {
        return senderID;
    }

    /**
     * Gets the receiver ID
     * @return The receiver ID
     */
    public String getReceiverID() {
        return receiverID;
    }

    /**
     * Gets the notification type
     * @return The notification type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the notification content
     * @return The notification content
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets the notification timestamp
     * @return The notification timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Checks if the notification has been read
     * @return True if the notification has been read, false otherwise
     */
    public boolean isRead() {
        return isRead;
    }

    /**
     * Marks the notification as read
     */
    public void markAsRead() {
        this.isRead = true;
    }

    /**
     * Gets the status of the notification
     * @return The status of the notification
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of the notification
     * @param status The new status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Creates a formatted string representation of the notification
     * @return A formatted string representation of the notification
     */

    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedTime = timestamp.format(formatter);

        String result;
        if (type.equals("follow_request") && status.equals("pending")) {
            result = "[" + formattedTime + "] You have a follow request from " + senderID + ": " + content;
        } else if (type.equals("follow_request")) {
            result = "[" + formattedTime + "] Follow request from " + senderID + " was " + status;
        } else if (type.equals("post")) {
            result = "[" + formattedTime + "] " + content;
        } else {
            result = "[" + formattedTime + "] " + content;
        }

        return result;
    }
}