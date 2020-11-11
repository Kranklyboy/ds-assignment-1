package dslab;

import dslab.exception.MalformedInputException;

import java.util.Objects;

public class Email {
    private String username;
    private String domain;

    public Email(String email) throws MalformedInputException {
        if (email.split("@").length != 2)
            throw new MalformedInputException("error email addresses must be of the form user@domain: " + email);
        if (email.split("@")[0].isBlank())
            throw new MalformedInputException("error email addresses must be of the form user@domain" + email);
        if (email.split("@")[1].isBlank())
            throw new MalformedInputException("error email addresses must be of the form user@domain" + email);

        this.username = email.split("@")[0];
        this.domain = email.split("@")[1];
    }

    public Email(String username, String domain) {
        this.username = username;
        this.domain = domain;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public String toString() {
        return username + '@' + domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(getUsername(), email.getUsername()) &&
                Objects.equals(getDomain(), email.getDomain());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername(), getDomain());
    }
}
