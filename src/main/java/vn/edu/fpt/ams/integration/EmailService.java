package vn.edu.fpt.ams.integration;

/** Single integration boundary for all application email. */
public class EmailService {
    public void sendPasswordReset(String address, String resetLink) { sendNotification(address, "AMS password reset", resetLink); }
    public void sendStudentInvitation(String address, String invitationLink) { sendNotification(address, "AMS class invitation", invitationLink); }
    public void sendAdditionalClassAssignmentOffer(String address, String offerLink) { sendNotification(address, "AMS class assignment offer", offerLink); }
    public void sendNotification(String address, String subject, String message) {
        throw new UnsupportedOperationException("TODO: centralize SMTP configuration, templates and delivery errors");
    }
}
