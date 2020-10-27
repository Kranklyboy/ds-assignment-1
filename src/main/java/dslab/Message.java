package dslab;

import dslab.exception.MissingInputException;

import java.util.ArrayList;

public class Message {
    private ArrayList<Email> to = new ArrayList<>();
    private Email from;
    private String subject;
    private String data;

    public Message() {
    }

    public Message(ArrayList<Email> to, Email from, String subject, String data) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.data = data;
    }

    public void allFieldsSet() throws MissingInputException {
        if (this.subject == null) this.subject = "";
        if (this.data == null) this.data = "";
        if (this.to.isEmpty()) throw new MissingInputException("error no receiver");
        if (this.from == null) throw new MissingInputException("error no sender");
    }

    public void addTo(Email email) {
        to.add(email);
    }

    public ArrayList<Email> getTo() {
        return to;
    }

    public void setTo(ArrayList<Email> to) {
        this.to = to;
    }

    public Email getFrom() {
        return from;
    }

    public void setFrom(Email from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
