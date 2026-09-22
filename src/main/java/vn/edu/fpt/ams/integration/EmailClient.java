package vn.edu.fpt.ams.integration;

/** Integration boundary shown in the System Administration class design. */
public interface EmailClient {
    void sendWelcomeEmail(String address, String temporaryPassword);
    void sendPasswordResetEmail(String address, String resetLink);
}
