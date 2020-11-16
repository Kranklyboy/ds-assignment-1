package dslab.mailbox;

import dslab.Email;
import dslab.Message;
import dslab.exception.MessageNotFoundException;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DMAPConnection implements Runnable {
    Logger logger = Logger.getLogger(DMAPConnection.class.getName());
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Email currentUser = null;
    private final ConcurrentHashMap<Email, LinkedList<Message>> storage;
    private final ConcurrentHashMap<String, String> userStorage;

    public DMAPConnection(Socket connection, ConcurrentHashMap<Email, LinkedList<Message>> storage, ConcurrentHashMap<String, String> userStorage) {
        this.socket = connection;
        this.storage = storage;
        this.userStorage = userStorage;
    }

    @Override
    public void run() {
        logger.finer("Preparing for DMAP communication in " + this.toString());
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ok DMAP");
            loginLoop();
            out.println("ok");

            String userInput;
            while (!Thread.currentThread().isInterrupted() && (userInput = in.readLine()) != null) {
                if ("quit".equals(userInput)) {
                    out.println("ok bye");
                    shutdown();
                } else if ("logout".equals(userInput)) {
                    out.println("ok");
                    currentUser = null;
                    loginLoop();
                } else if ("list".equals(userInput)) {
                    listMessages();
                } else if ("delete".equals(userInput.split("\\s+")[0])) {
                    if (userInput.split("\\s+").length == 2) {
                        deleteMessage(userInput.split("\\s+")[1]);
                    } else {
                        out.println("Please supply a message id to delete!");
                    }
                } else if ("show".equals(userInput.split("\\s+")[0])) {
                    if (userInput.split("\\s+").length == 2) {
                        showMessage(userInput.split("\\s+")[1]);
                    } else {
                        out.println("Please supply a message id to show!");
                    }
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
        } catch (MessageNotFoundException e) {
            out.println(e.getMessage());
        }
    }

    private void loginLoop() {
        String userInput;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                userInput = in.readLine();
                if ("quit".equals(userInput.split("\\s+")[0])) {
                    out.println("ok bye");
                    shutdown();
                } else if ("login".equals(userInput.split("\\s+")[0])) {
                    String[] args = userInput.split("\\s+");
                    if (args.length != 3)
                        out.println("Please specify a username and password to login!");
                    if (this.userStorage.containsKey(args[1])) {
                        // Check if username exists
                        if (args[2].equals(this.userStorage.get(args[1]))) {
                            // Check if password matches
                            for (Email email : this.storage.keySet()) {
                                if (args[1].equals(email.getUsername())) {
                                    // Set current user if login successful
                                    currentUser = email;
                                    logger.info("User successfully logged in: " + currentUser.toString());
                                    return;
                                }
                            }
                        } else {
                            out.println("error wrong password");
                        }
                    } else {
                        out.println("error unknown user");
                    }
                } else {
                    out.println("error not logged in");
                }
            }
        } catch (InterruptedIOException ioe) {
            logger.info("Received interrupt from parent. Shutting down...");
            shutdown();
        } catch (SocketException e) {
            logger.finer("Received interrupt. Exiting " + this.toString());
            shutdown();
        } catch (IOException e) {
            logger.severe("Failed to get IO-Stream");
            e.printStackTrace();
            shutdown();
        }
    }

    private void showMessage(String id) throws MessageNotFoundException {
        int i;
        try {
            i = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new MessageNotFoundException("error unknown message id");
        }

        for (Message m : storage.get(currentUser)) {
            if (m.getId() == i) {
                out.println(m);
                return;
            }
        }

        throw new MessageNotFoundException("error unknown message id");
    }

    private void listMessages() {
        if (storage.get(currentUser).isEmpty()) {
            out.println("You do not have any messages at the moment!");
            return;
        }

        for (Message m : storage.get(currentUser)) {
            out.println(m.listMessage());
        }
    }

    public void deleteMessage (String id) throws MessageNotFoundException {
        int i;
        try {
            i = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new MessageNotFoundException("error unknown message id");
        }

        for (Message m : storage.get(currentUser)) {
            if (m.getId() == i) {
                storage.get(currentUser).remove(m);
                return;
            }
        }

        throw new MessageNotFoundException("error unknown message id");
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
