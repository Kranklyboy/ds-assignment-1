package dslab.transfer;

import dslab.Message;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientListener extends Thread {
    private final ServerSocket serverSocket;
    private final Logger logger = Logger.getLogger(ClientListener.class.getName());
    private final ArrayList<ClientConnection> clients = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final BlockingQueue<Message> blockingQueue;

    public ClientListener(ServerSocket serverSocket, BlockingQueue<Message> blockingQueue) {
        this.serverSocket = serverSocket;
        this.blockingQueue = blockingQueue;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            logger.finer("Waiting for request on serverSocket " + serverSocket.toString());
            try {
                Socket s = serverSocket.accept();
                logger.fine("Processing incoming socket " + s.toString());
                ClientConnection clientConnection = new ClientConnection(s, this.blockingQueue);
                clients.add(clientConnection);
                executorService.submit(clientConnection);
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
        logger.finer("Shutting down ClientListener " + this.toString());
        for (ClientConnection client : clients) {
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
