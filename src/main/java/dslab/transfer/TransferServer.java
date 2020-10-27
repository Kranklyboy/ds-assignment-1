package dslab.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {
    Logger logger = Logger.getLogger(TransferServer.class.getName());
    private ServerSocket serverSocket;
    private final Shell shell;
    private final Integer serverPort;
    private ArrayList<Config> mailboxServers;


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        Config earthPlanet = new Config("mailbox-earth-planet");
        Config univerZe = new Config("mailbox-univer-ze");
        // TODO parse domains for mailbox servers
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("Transferserver> ");
        this.serverPort = config.getInt("tcp.port");
    }

    @Override
    public void run() {
        logger.info("Creating serverSocket for " + this.toString());
        try {
            this.serverSocket = new ServerSocket(serverPort);
        } catch (IOException e) {
            logger.severe("Error creating serverSocket " + serverSocket.toString());
            e.printStackTrace();
            shutdown();
        }
        new ClientListener(serverSocket).start();
        this.shell.run();
    }

    @Command
    @Override
    public void shutdown() {
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            logger.severe("Error closing serverSocket " + serverSocket.toString());
            e.printStackTrace();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }
}
