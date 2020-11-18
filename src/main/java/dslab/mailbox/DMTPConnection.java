package dslab.mailbox;

import dslab.Email;
import dslab.Message;
import dslab.exception.MalformedInputException;
import dslab.exception.UnknownRecipientException;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DMTPConnection implements Runnable {
    Logger logger = Logger.getLogger(DMTPConnection.class.getName());
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private Message msg = new Message();

    private final String domain;

    private final ConcurrentHashMap<Email, LinkedList<Message>> messageStorage;
    private final ConcurrentHashMap<String, String> userStorage;

    public DMTPConnection(Socket connection, ConcurrentHashMap<Email, LinkedList<Message>> messageStorage, ConcurrentHashMap<String, String> userStorage, String domain) {
        this.socket = connection;
        this.messageStorage = messageStorage;
        this.userStorage = userStorage;
        this.domain = domain;
    }

    @Override
    public void run() {
        logger.finer("Preparing for DMTP communication in " + this.toString());
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ok DMTP");

            String userInput;

            try {
                if (!("begin".equals(in.readLine()))) {
                    out.println("error protocol error");
                    shutdown();
                }
            } catch (SocketException e) {
                // Thrown if socket has already been closed by shutdown() method
                logger.finer("Received interrupt. Exiting " + this.toString());
                shutdown();
            }
            out.println("ok");

            while (!Thread.currentThread().isInterrupted() && (userInput = in.readLine()) != null) {
                if ("quit".equals(userInput)) {
                    out.println("ok bye");
                    shutdown();
                } else if ("send".equals(userInput)) {
                    storeMessage();
                    out.println("ok");
                } else if ("to".equals(userInput.split("\\s+")[0])) {
                    msg.setTo(new ArrayList<>());
                    String[] emailAddresses = userInput.split("\\s+")[1].split(",");
                    logger.info("Split EMail addresses: " + Arrays.toString(emailAddresses));
                    int count = 0;
                    try {
                        for (String emailAddress : emailAddresses) {
                            logger.info("Current email address in msg.getTo(): " + emailAddress);
                            Email add = new Email(emailAddress);
                            if (this.domain.equals(add.getDomain())) {
                                logger.info("Address " + emailAddress + " belongs to this domain " + this.domain);
                                if (!this.userStorage.containsKey(add.getUsername())) {
                                    logger.info("Our userStorage in domain " + this.domain + " does not contain user " + add.getUsername());
                                    out.println("error unknown recipient " + add.toString());
                                } else {
                                    logger.info("Address " + add.toString() + " belongs to this domain and user exists. Adding address to msg.To() field");
                                    msg.addTo(add);
                                    count++;
                                }
                            } else {
                                logger.info("Address " + emailAddress + " does not belong to this domain. Adding anyway...");
                                msg.addTo(add);
                                count++;
                            }
                        }
                        out.println("ok " + count);
                    } catch (MalformedInputException e) {
                        out.println(e.getMessage());
                    }
                } else if ("from".equals(userInput.split("\\s+")[0])) {
                    try {
                        Email from = new Email(userInput.split("\\s+")[1]);
                        this.msg.setFrom(from);
                        out.println("ok");
                    } catch (MalformedInputException mie) {
                        out.println(mie.getMessage());
                    }
                } else if ("subject".equals(userInput.split("\\s+")[0])) {
                    String subject = "";
                    if (userInput.split("\\s+").length > 1)
                        subject = userInput.split("\\s+", 2)[1];
                    msg.setSubject(subject);
                    out.println("ok");
                } else if ("data".equals(userInput.split("\\s+")[0])) {
                    String data = userInput.split("\\s+", 2)[1];
                    msg.setData(data);
                    out.println("ok");
                } else {
                    out.println("error protocol error");
                    shutdown();
                }
            }
        } catch (InterruptedIOException ioe) {
            logger.info("Received interrupt from parent. Shutting down...");
            shutdown();
        } catch (IOException e) {
            logger.severe("Failed to get IO-Stream");
            e.printStackTrace();
            shutdown();
        }
    }

    private synchronized void storeMessage() {
        logger.info("Storing message " + msg.toString());
        this.msg.setId(MailboxServer.id++);
        boolean alreadyPresent;
        for (Email recipient : this.msg.getTo()) {
            alreadyPresent = false;
            logger.info("storeMessage(): checking if msg " + msg.listMessage() + " already exists for recipient " + recipient.toString());
            if (this.messageStorage.containsKey(recipient)) {
                // Check if message already exists for user
                for (Message m : this.messageStorage.get(recipient)) {
                    if (this.msg.getId().equals(m.getId())) {
                        logger.info("storeMessage(): msg " + msg.listMessage() + " already exists for recipient " + recipient.toString());
                        alreadyPresent = true;
                        break;
                    }
                }
                // Save only if message doesn't already exist
                if (!alreadyPresent) {
                    this.messageStorage.get(recipient).add(this.msg);
                }
            }
        }
        this.msg = new Message();
    }

    public void shutdown() {
        logger.info("Shutting down client connection " + this.toString());
        try {
            if (socket != null)
                socket.close();
            in.close();
            out.close();
        } catch (IOException e) {
            logger.severe("Error closing socket and/or IO-streams");
            e.printStackTrace();
        }
        Thread.currentThread().interrupt();
    }
}
