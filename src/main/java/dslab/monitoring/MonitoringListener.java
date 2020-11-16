package dslab.monitoring;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class MonitoringListener extends Thread {
    private static final Logger logger = Logger.getLogger(MonitoringListener.class.getName());
    private final DatagramSocket serverSocket;
    private final ConcurrentHashMap<String, Integer> addresses;
    private final ConcurrentHashMap<String, Integer> servers;

    public MonitoringListener(DatagramSocket serverSocket, ConcurrentHashMap<String, Integer> addresses, ConcurrentHashMap<String, Integer> servers) {
        this.serverSocket = serverSocket;
        this.addresses = addresses;
        this.servers = servers;
    }

    @Override
    public void run() {
        byte[] recvBuffer = new byte[1024];
        while (!Thread.currentThread().isInterrupted()) {
            logger.finer("Waiting for request on UDP serverSocket " + serverSocket.toString());
            try {
                DatagramPacket packet = new DatagramPacket(recvBuffer, recvBuffer.length);
                serverSocket.receive(packet);
                logger.fine("Processing incoming socket " + serverSocket.toString());
                String receivedData = new String(packet.getData(), StandardCharsets.UTF_8).trim();
                String server = receivedData.split("\\s+")[0];
                String address = receivedData.split("\\s+")[1];
                logger.info("Incoming packet contains server: " + server);
                logger.info("Incoming packet contains address: " + address);
                // Add/Update num of packets sent by server
                if (!this.servers.containsKey(server))
                    this.servers.put(server, 1);
                else
                    this.servers.put(server, this.servers.get(server) + 1);
                // Add/Update num of packets sent for address
                if (!this.addresses.containsKey(address))
                    this.addresses.put(address, 1);
                else
                    this.addresses.put(address, this.addresses.get(address) + 1);
            } catch (InterruptedIOException | SocketException e) {
                logger.finer("Received interrupt. Exiting " + this.toString());
                this.shutdown();
            } catch (IOException e) {
                logger.severe("Error starting serverSocket " + serverSocket.toString());
                e.printStackTrace();
                this.shutdown();
            }
        }
    }

    public void shutdown() {
        logger.finer("Shutting down MonitoringListener " + this.toString());
        this.serverSocket.close();
        this.interrupt();
    }
}
