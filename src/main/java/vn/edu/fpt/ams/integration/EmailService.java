package vn.edu.fpt.ams.integration;

/** Single integration boundary for all application email. */
public class EmailService {
  public void sendPasswordReset(String address, String resetLink) {
    sendNotification(address, "AMS password reset", resetLink);
  }

  public void sendStudentInvitation(String address, String invitationLink) {
    sendNotification(address, "AMS class invitation", invitationLink);
  }

  public void sendAdditionalClassAssignmentOffer(String address, String offerLink) {
    sendNotification(address, "AMS class assignment offer", offerLink);
  }

  public void sendNotification(String address, String subject, String message) {
    var p = new java.util.Properties();
    p.setProperty(
        "mail.smtp.host", vn.edu.fpt.ams.util.AppConfig.required("SMTP_HOST", "smtp.host"));
    p.setProperty(
        "mail.smtp.port", vn.edu.fpt.ams.util.AppConfig.get("SMTP_PORT", "smtp.port", "587"));
    p.setProperty("mail.smtp.starttls.enable", "true");
    p.setProperty("mail.smtp.starttls.required", "true");
    p.setProperty("mail.smtp.connectiontimeout", "10000");
    p.setProperty("mail.smtp.timeout", "10000");
    p.setProperty("mail.smtp.writetimeout", "10000");
    String user = vn.edu.fpt.ams.util.AppConfig.get("SMTP_USERNAME", "smtp.username", "");
    String password = vn.edu.fpt.ams.util.AppConfig.get("SMTP_PASSWORD", "smtp.password", "");
    p.setProperty("mail.smtp.auth", Boolean.toString(!user.isBlank()));
    var session = jakarta.mail.Session.getInstance(p);
    try {
      var mail = new jakarta.mail.internet.MimeMessage(session);
      mail.setFrom(
          new jakarta.mail.internet.InternetAddress(
              vn.edu.fpt.ams.util.AppConfig.required("SMTP_FROM", "smtp.from")));
      mail.setRecipients(
          jakarta.mail.Message.RecipientType.TO,
          jakarta.mail.internet.InternetAddress.parse(address, true));
      mail.setSubject(subject, "UTF-8");
      mail.setText(message, "UTF-8");
      try (var transport = session.getTransport("smtp")) {
        transport.connect(
            p.getProperty("mail.smtp.host"),
            Integer.parseInt(p.getProperty("mail.smtp.port")),
            user.isBlank() ? null : user,
            user.isBlank() ? null : password);
        transport.sendMessage(mail, mail.getAllRecipients());
      }
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException("Email could not be delivered", e);
    }
  }
}
