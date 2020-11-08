package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {
    private static final Logger logger = Logger.getLogger(MailboxServer.class.getName());
    private ServerSocket dmtpServerSocket;
    private ServerSocket dmapServerSocket;
    private final Shell shell;
    private final Integer dmtpServerPort;
    private final Integer dmapServerPort;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        // TODO initialize email and user storage (concurrent hashmap?)
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt("Mailboxserver> ");
        this.dmtpServerPort = config.getInt("dmtp.tcp.port");
        this.dmapServerPort = config.getInt("dmap.tcp.port");
    }

    @Override
    public void run() {
        logger.info("Creating DMTP serverSocket for TransferServer + " + this.toString());
        try {
            this.dmtpServerSocket = new ServerSocket(dmtpServerPort);
            this.dmapServerSocket = new ServerSocket(dmapServerPort);
        } catch (IOException e) {
            logger.severe("Error creating one of the two server sockets");
            e.printStackTrace();
            shutdown();
        }
        // TODO spawn listener for transfer servers
        // TODO spawn listener for user clients
        this.shell.run();
    }

    @Command
    @Override
    public void shutdown() {
        try {
            if (dmtpServerSocket != null)
                dmtpServerSocket.close();
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
