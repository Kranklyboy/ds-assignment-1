package dslab.mailbox;

import dslab.Email;
import dslab.Message;
import dslab.transfer.ClientConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DMAPListener extends Thread {
    private final ServerSocket serverSocket;
    private final Logger logger = Logger.getLogger(DMAPListener.class.getName());
    private final ConcurrentHashMap<Email, LinkedList<Message>> storage;
    private final ConcurrentHashMap<String, String> userStorage;
    private final ArrayList<DMAPConnection> clients = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public DMAPListener(ServerSocket serverSocket, ConcurrentHashMap<Email, LinkedList<Message>> storage, ConcurrentHashMap<String, String> userStorage) {
        this.serverSocket = serverSocket;
        this.storage = storage;
        this.userStorage = userStorage;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            logger.finer("Waiting for request on serverSocket " + serverSocket.toString());
            try {
                Socket s = serverSocket.accept();
                logger.fine("Processing incoming socket " + s.toString());
                DMAPConnection dmapConnection = new DMAPConnection(s, storage, userStorage);
                clients.add(dmapConnection);
                executorService.submit(dmapConnection);
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
        logger.finer("Shutting down DMAPListener " + this.toString());
        for (DMAPConnection client : clients) {
            if (client != null)
                client.shutdown();
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
        this.interrupt();
    }
}
