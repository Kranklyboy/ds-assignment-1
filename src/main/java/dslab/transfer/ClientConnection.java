package dslab.transfer;

import dslab.Email;
import dslab.Message;
import dslab.exception.MalformedInputException;
import dslab.exception.MissingInputException;
import dslab.exception.UnknownRecipientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientConnection implements Runnable {
    Logger logger = Logger.getLogger(ClientConnection.class.getName());
    private Socket socket;

    private Message msg;

    public ClientConnection(Socket connection) {
        this.socket = connection;
    }

    @Override
    public void run() {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ok DMTP");

            String userInput;

            if (!("begin".equals(in.readLine()))) {
                out.println("error protocol error");
                shutdown();
            }

            while ((userInput = in.readLine()) != null) {
                if ("quit".equals(userInput)) {
                    out.println("ok bye");
                    shutdown();
                } else if ("send".equals(userInput)) {
                    try {
                        sendMessage();
                        out.println("ok");
                    } catch (MissingInputException e) {
                        out.println(e.getMessage());
                    }
                } else if ("to".equals(userInput.split("\\s+")[0])) {
                    String[] emailAddresses = userInput.split("\\s+")[1].split(",");
                    int count = 0;
                    for (String emailAddress : emailAddresses) {
                        try {
                            msg.addTo(new Email(emailAddress));
                            count++;
                        } catch (MalformedInputException mie) {
                            out.println(mie.getMessage());
                        }
                    }
                    out.println("ok " + count);
                } else if ("from".equals(userInput.split("\\s+")[0])) {
                    try {
                        msg.setFrom(new Email(userInput.split("\\s+")[1]));
                    } catch (MalformedInputException mie) {
                        out.println(mie.getMessage());
                    }
                    out.println("ok");
                } else if ("subject".equals(userInput.split("\\s+")[0])) {
                    String subject = userInput.split("\\s+", 1)[1];
                    logger.info("Setting subject to: " + subject);
                    msg.setSubject(subject);
                    out.println("ok");
                } else if ("data".equals(userInput.split("\\s+")[0])) {
                    String data = userInput.split("\\s+", 1)[1];
                    logger.info("Setting data to: " + data);
                    msg.setData(data);
                    out.println("ok");
                } else {
                    out.println("error protocol error");
                    shutdown();
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to get IO-Stream");
            e.printStackTrace();
            shutdown();
        }
    }

    public void shutdown() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.severe("Error closing socket " + socket.toString());
            e.printStackTrace();
        }
    }

    public void sendMessage() throws MissingInputException {
        msg.allFieldsSet();
        // TODO send message to mailbox servers or submit to asynchronous queue
    }
}
