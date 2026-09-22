package vn.edu.fpt.ams.integration;

/** SMTP integration is not configured in the initial codebase. */
public class EmailService implements EmailClient {
    @Override public void sendWelcomeEmail(String address, String temporaryPassword) {
        throw new UnsupportedOperationException("TODO: configure secure email delivery");
    }
    @Override public void sendPasswordResetEmail(String address, String resetLink) {
        throw new UnsupportedOperationException("TODO: configure secure email delivery");
    }
}
