package server;
import java.net.InetAddress;
// Stores network connection info for a client.
public class ClientInfo {
    private String clientID;
    private InetAddress ipAddress;
    private int port;
    public ClientInfo(String clientID, InetAddress ipAddress, int port) {
        this.clientID = clientID;
        this.ipAddress = ipAddress;
        this.port = port;
    }
    public String getClientID() {
        return clientID;
    }
    public InetAddress getIpAddress() {
        return ipAddress;
    }
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