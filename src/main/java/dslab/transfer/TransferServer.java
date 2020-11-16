package dslab.transfer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Email;
import dslab.Message;
import dslab.exception.MalformedInputException;
import dslab.exception.UnknownDomain;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {
    private static final Logger logger = Logger.getLogger(TransferServer.class.getName());
    private ServerSocket serverSocket;
    private final Shell shell;
    private final Integer serverPort;
    public static final HashMap<String, Integer> mailboxServers = new HashMap<>();
    public static volatile BlockingQueue<Message> blockingQueue = new LinkedBlockingQueue<>(10);
    private final Consumer consumer;
    public static final Object lock = new Object();


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        Config univerze = new Config("mailbox-univer-ze.properties");
        Config earthplanet = new Config("mailbox-earth-planet.properties");
        mailboxServers.put(univerze.getString("domain"), univerze.getInt("dmtp.tcp.port"));
        mailboxServers.put(earthplanet.getString("domain"), earthplanet.getInt("dmtp.tcp.port"));
        this.consumer = new Consumer(mailboxServers);
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
        this.consumer.start();
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
        this.consumer.interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

    static class Producer extends Thread {
        private final Message msg;

        Producer(Message msg) {
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
            synchronized (lock) {
                while (blockingQueue.size() == 10) {
                    lock.wait();
                }
                blockingQueue.put(msg);
                logger.info("Added message " + msg.toString() + " to queue");
                lock.notify();
            }
        }

        private void shutdown() {
            Thread.currentThread().interrupt();
        }
    }

    static class Consumer extends Thread {
        private final HashMap<String, Integer> mailboxServers;

        Consumer(HashMap<String, Integer> mailboxServers) {
            this.mailboxServers = mailboxServers;
            logger.info("MailboxServers: " + mailboxServers.toString());
        }

        @Override
        public void run() {
            try {
                consume();
            } catch (InterruptedException e) {
                logger.info("Consumer thread has been interrupted. Exiting..." + this.toString());
                shutdown();
            }
        }

        private void consume() throws InterruptedException {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (lock) {
                    while (blockingQueue.isEmpty()) {
                        logger.info("Queue currently empty. Waiting for messages to appear...");
                        lock.wait();
                    }
                    logger.info("Queue not empty. Processing message...");
                    Message msg = blockingQueue.take();
                    logger.info("Took message " + msg.toString() + " from queue");
                    for (Email recipient : msg.getTo()) {
                        int port;
                        try {
                            port = domainLookup(recipient);
                            logger.info("Domain lookup successful. Port is: " + port);
                        } catch (UnknownDomain e) {
                            sendErrorMail(msg, e.getMessage());
                            // TODO exit consumer?
                            shutdown();
                            continue;
                        }
                        replayMessage(msg, port);
                    }
                    lock.notify();
                }
            }
        }

        private void replayMessage(Message msg, int port) {
            logger.info("Replaying message for message: " + msg.toString() + " on port " + port);
            try {
                Socket socket = new Socket("127.0.0.1", port);
                PrintWriter socketOut = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socketIn.readLine();
                socketOut.println("begin");
                socketIn.readLine();
                socketOut.println("subject " + msg.getSubject());
                socketIn.readLine();
                socketOut.println("data " + msg.getData());
                socketIn.readLine();
                socketOut.println("to " + msg.printTo());
                socketIn.readLine();
                socketOut.println("from " + msg.getFrom().toString());
                socketIn.readLine();
                socketOut.println("send");
                String result = socketIn.readLine();
                if (result.startsWith("error"))
                    sendErrorMail(msg, result);
                socketIn.close();
                socketOut.close();
                socket.close();
            } catch (IOException e) {
                sendErrorMail(msg, "error failed to connect to server");
            }
        }

        private int domainLookup(Email email) throws UnknownDomain {
            logger.info("Performing domain lookup for address " + email.toString());
            if (this.mailboxServers.containsKey(email.getDomain()))
                return this.mailboxServers.get(email.getDomain());
            throw new UnknownDomain("error domain not found: " + email.getDomain());
        }

        private void sendErrorMail(Message msg, String error) {
            logger.info("Trying to send error mail to address " + msg.getFrom());
            int port;
            try {
                port = domainLookup(msg.getFrom());
            } catch (UnknownDomain e) {
                logger.severe("Sending error mail failed because sender domain is unknown");
                return;
            }
            ArrayList<Email> newTo = new ArrayList<>();
            newTo.add(msg.getFrom());
            msg.setTo(newTo);
            try {
                msg.setFrom(new Email("mailer@127.0.0.1"));
            } catch (MalformedInputException e) {
                logger.severe("The server's E-Mail address is wrong. This should not be happening!");
                e.printStackTrace();
                return;
            }
            try {
                Socket socket = new Socket("127.0.0.1", port);
                PrintWriter socketOut = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socketIn.readLine();
                socketOut.println("begin");
                socketIn.readLine();
                socketOut.println("subject " + msg.getSubject());
                socketIn.readLine();
                socketOut.println("data " + error);
                socketIn.readLine();
                socketOut.println("to " + msg.printTo());
                socketIn.readLine();
                socketOut.println("from " + msg.getFrom().toString());
                socketIn.readLine();
                socketOut.println("send");
                String result = socketIn.readLine();
                if (result.startsWith("error"))
                    logger.severe("Sending error mail failed: " + result);
                socketIn.close();
                socketOut.close();
                socket.close();
            } catch (IOException e) {
                logger.severe("Sending error mail failed because socket communication failed");
            }
        }

        private void shutdown() {
            Thread.currentThread().interrupt();
        }
    }
}
