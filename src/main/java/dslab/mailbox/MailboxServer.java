package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Email;
import dslab.Message;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {
    private static final Logger logger = Logger.getLogger(MailboxServer.class.getName());
    private final String domain;
    private ServerSocket dmtpServerSocket;
    private ServerSocket dmapServerSocket;
    private final Shell shell;
    private final Integer dmtpServerPort;
    private final Integer dmapServerPort;
    private DMTPListener dmtpListener;
    private DMAPListener dmapListener;
    private final ConcurrentHashMap<Email, LinkedList<Message>> messageStorage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userStorage = new ConcurrentHashMap<>();

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.domain = config.getString("domain");
        Config userConfig = new Config(config.getString("users.config"));

        // Load users from config into userStorage for authentication
        for (String key : userConfig.listKeys()) {
            userStorage.put(key, userConfig.getString(key));
        }
        // Load Email Addresses into messageStorage
        for (String key : userStorage.keySet()) {
            Email current = new Email(key, domain);
            messageStorage.put(current, new LinkedList<>());
        }

        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("Mailboxserver> ");
        this.dmtpServerPort = config.getInt("dmtp.tcp.port");
        this.dmapServerPort = config.getInt("dmap.tcp.port");
    }

    @Override
    public void run() {
        logger.info("Creating DMTP and DMAP serverSockets for MailboxServer + " + this.toString());
        try {
            this.dmtpServerSocket = new ServerSocket(dmtpServerPort);
            this.dmapServerSocket = new ServerSocket(dmapServerPort);
        } catch (IOException e) {
            logger.severe("Error creating one of the two server sockets");
            e.printStackTrace();
            shutdown();
        }
        this.dmtpListener = new DMTPListener(this.dmtpServerSocket, this.messageStorage);
        this.dmtpListener.start();
        // TODO spawn listener for user clients (DMAPListener)
        this.shell.run();
    }

    @Command
    @Override
    public void shutdown() {
        // TODO shutdown DMTPListener
        // TODO shutdown DMAPListener
        try {
            if (dmtpServerSocket != null)
                dmtpServerSocket.close();
            this.dmtpListener.interrupt();
        } catch (IOException e) {
            logger.severe("Error closing DMTP serverSocket " + dmtpServerSocket.toString());
            e.printStackTrace();
        }

        try {
            if (dmapServerSocket != null)
                dmapServerSocket.close();
        } catch (IOException e) {
            logger.severe("Error closing DMTP serverSocket " + dmapServerSocket.toString());
            e.printStackTrace();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
