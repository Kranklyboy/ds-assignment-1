package dslab.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import dslab.ComponentFactory;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {
    Logger logger = Logger.getLogger(TransferServer.class.getName());

    private ServerSocket serverSocket;
    private Integer serverPort;

    private ExecutorService executorService = Executors.newFixedThreadPool(20);

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
        mailboxServers.add(earthPlanet);
        mailboxServers.add(univerZe);
        Shell shell = new Shell().register("quit", (input, context) -> shutdown());
        this.serverPort = config.getInt("tcp.port");

        shell.run();
    }

    @Override
    public void run() {
        try {
            logger.info("Creating serverSocket for " + this.toString());
            serverSocket = new ServerSocket(serverPort);

            // TODO stop server on quit command over shell
            while (true) {
                logger.info("Waiting for request on serverSocket " + serverSocket.toString());
                try {
                    Socket s = serverSocket.accept();
                    logger.info("Processing incoming socket " + s.toString());
                    executorService.submit(new ClientConnection(s));
                } catch (IOException ioe) {
                    logger.severe("Error starting serverSocket " + serverSocket.toString());
                    ioe.printStackTrace();
                }
            }
        } catch (IOException e) {
            logger.severe("Error creating serverSocket " + serverSocket.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.severe("Error closing serverSocket " + serverSocket.toString());
            e.printStackTrace();
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
