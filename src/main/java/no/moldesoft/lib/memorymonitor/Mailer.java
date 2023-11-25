package no.moldesoft.lib.memorymonitor;

import no.moldesoft.lib.exceptions.NotImplementedException;

public interface Mailer {
    default void sendMail(String from, String to, String subject, String body) {
        sendMail(subject, body);
    }

    default void sendMail(String subject, String body) {
        throw new NotImplementedException();
    }

    default void sendMail(String body) {
        throw new NotImplementedException();
    }
}
