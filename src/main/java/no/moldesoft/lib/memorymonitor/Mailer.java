package no.moldesoft.lib.memorymonitor;

public interface Mailer {
    void sendMail(String from, String to, String subject, String body);
}
