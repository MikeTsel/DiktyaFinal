package model;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// Represents a notification exchanged in the system.
public class Notification {
    private String senderID;
    private String receiverID;
    private String type;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;
    private String status; 
    public Notification(String senderID, String receiverID, String type, String content) {
        this.senderID = senderID;
        this.receiverID = receiverID;
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
        if (type.equals("follow_request") || type.equals("photo_request")) {
            this.status = "pending";
        } else {
            this.status = "none";
        }
    }
    public String getSenderID() {
        return senderID;
    }
    public String getReceiverID() {
        return receiverID;
    }
    public String getType() {
        return type;
    }
    public String getContent() {
        return content;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public boolean isRead() {
        return isRead;
    }
    public void markAsRead() {
        this.isRead = true;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
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