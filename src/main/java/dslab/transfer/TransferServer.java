package dslab.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Message;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {
    private static final Logger logger = Logger.getLogger(TransferServer.class.getName());
    private ServerSocket serverSocket;
    private final Shell shell;
    private final Integer serverPort;
    private ArrayList<Config> mailboxServers;
    private final BlockingQueue<Message> blockingQueue = new LinkedBlockingQueue<>(10);


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
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
        new ClientListener(serverSocket, blockingQueue).start();
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

    static class Producer implements Runnable {
        private final BlockingQueue<Message> blockingQueue;
        private final Message msg;

        Producer(BlockingQueue<Message> blockingQueue, Message msg) {
            this.blockingQueue = blockingQueue;
            this.msg = msg;
        }

        @Override
        public void run() {
            try {
                produce();
            } catch (InterruptedException e) {
                logger.info("Producer thread has been interrupted. Exiting..." + this.toString());
                shutdown();
            }
        }

        private void produce() throws InterruptedException {
            synchronized (this) {
                while (blockingQueue.size() == 10)
                    wait();
                blockingQueue.put(msg);
                logger.info("Added message " + msg.toString() + " to queue");
                notify();
            }
        }

        private void shutdown() {
            Thread.currentThread().interrupt();
        }
    }

    class Consumer implements Runnable {
        private final BlockingQueue<Message> blockingQueue;
        private Socket socket;

        Consumer(BlockingQueue<Message> blockingQueue) {
            this.blockingQueue = blockingQueue;
        }

        @Override
        public void run() {
            try {
                consume();
            } catch (InterruptedException e) {
                logger.info("Consumer thread has been interrupted. Exiting..." + this.toString());
                shutdown();
            } // TODO catch exception where recipient not known to server and send message to "from" user
        }

        private void consume() throws InterruptedException {
            while (true) {
                synchronized (this) {
                    while (blockingQueue.size() == 0)
                        wait();
                    Message msg = blockingQueue.take();
                    logger.info("Took message " + msg.toString() + " from queue");
                    // TODO open socket to all MailboxServers specified in "to" field
                    // TODO replay DMTP message
                    // TODO wait for reply and throw "recipient unknown" if unknown email in "to" field.
                    notify();
                }
            }
        }

        private void shutdown() {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                logger.severe("Failed to close socket during shutdown" + socket.toString());
                e.printStackTrace();
            }
            Thread.currentThread().interrupt();
        }
    }
}
