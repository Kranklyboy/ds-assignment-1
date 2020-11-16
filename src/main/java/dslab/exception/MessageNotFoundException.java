package dslab.exception;

public class MessageNotFoundException extends Exception {
    public MessageNotFoundException(String errorMessage) {
        super(errorMessage);
    }
}
