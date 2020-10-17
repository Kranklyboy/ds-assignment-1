package dslab.exception;

public class UnknownRecipientException extends Exception {
    public UnknownRecipientException(String errorMessage) {
        super(errorMessage);
    }
}
