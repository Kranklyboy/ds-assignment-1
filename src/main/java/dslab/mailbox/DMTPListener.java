package dslab.mailbox;

import dslab.Email;
import dslab.Message;

import java.io.IOException;
import java.io.InterruptedIOException;
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

public class DMTPListener extends Thread {
    private final ServerSocket serverSocket;
    private final Logger logger = Logger.getLogger(DMTPListener.class.getName());
    private final ArrayList<DMTPConnection> clients = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Email, LinkedList<Message>> storage;

    public DMTPListener(ServerSocket serverSocket, ConcurrentHashMap<Email, LinkedList<Message>> storage) {
        this.serverSocket = serverSocket;
        this.storage = storage;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            logger.finer("Waiting for request on serverSocket " + serverSocket.toString());
            try {
                Socket s = serverSocket.accept();
                logger.fine("Processing incoming socket " + s.toString());
                DMTPConnection dmtpConnection = new DMTPConnection(s, storage);
                clients.add(dmtpConnection);
                executorService.submit(dmtpConnection);
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
        logger.finer("Shutting down DMTPHandler " + this.toString());
        for (DMTPConnection client : clients) {
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
