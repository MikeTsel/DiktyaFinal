package server;

import java.net.InetAddress;

/**
 * Class to store client connection information.
 * Contains the client ID, IP address, and port number.
 */
public class ClientInfo {
    private String clientID;
    private InetAddress ipAddress;
    private int port;

    /**
     * Constructor for ClientInfo
     * @param clientID The ID of the client
     * @param ipAddress The IP address of the client
     * @param port The port number of the client
     */
    public ClientInfo(String clientID, InetAddress ipAddress, int port) {
        this.clientID = clientID;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    /**
     * Gets the client ID
     * @return The client ID
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * Gets the client's IP address
     * @return The client's IP address
     */
    public InetAddress getIpAddress() {
        return ipAddress;
    }

    /**
     * Gets the client's port number
     * @return The client's port number
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "clientID='" + clientID + '\'' +
                ", ipAddress=" + ipAddress.getHostAddress() +
                ", port=" + port +
                '}';
    }
}