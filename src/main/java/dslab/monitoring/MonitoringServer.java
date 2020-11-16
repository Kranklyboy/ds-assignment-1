package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {
    private static final Logger logger = Logger.getLogger(MonitoringServer.class.getName());
    private DatagramSocket serverSocket;
    private final Shell shell;
    private final Integer serverPort;
    private MonitoringListener monitoringListener;
    private final ConcurrentHashMap<String, Integer> addresses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> servers = new ConcurrentHashMap<>();

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("MonitoringServer> ");
        this.serverPort = config.getInt("udp.port");
    }

    @Override
    public void run() {
        logger.info("Creating UDP serverSocket for " + this.toString());
        try {
            this.serverSocket = new DatagramSocket(serverPort);
        } catch (IOException e) {
            logger.severe("Error creating serverSocket " + serverSocket.toString());
            e.printStackTrace();
            shutdown();
        }
        this.monitoringListener = new MonitoringListener(serverSocket, addresses, servers);
        this.monitoringListener.start();
        this.shell.run();
    }

    @Command
    @Override
    public void addresses() {
        for (String address : addresses.keySet()) {
            this.shell.out().println(address + " " + addresses.get(address));
        }
    }

    @Command
    @Override
    public void servers() {
        for (String server : servers.keySet()) {
            this.shell.out().println(server + " " + servers.get(server));
        }
    }

    @Command
    @Override
    public void shutdown() {
        if (serverSocket != null)
            serverSocket.close();
        this.monitoringListener.interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }
}
