package com.example.bacoorconnect.Helpers;

import android.os.AsyncTask;
import android.util.Log;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class JavaMailAPI extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "JavaMailAPI";

    private String email, subject, message;
    private String senderEmail, senderPassword;
    private MailCallback callback;

    public JavaMailAPI(String senderEmail, String senderPassword, String email, String subject, String message, MailCallback callback) {
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.callback = callback;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, senderPassword);
                }
            });

            Message mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(senderEmail));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            mimeMessage.setSubject(subject);
            mimeMessage.setText(message);

            Transport.send(mimeMessage);
            return true;

        } catch (MessagingException e) {
            Log.e(TAG, "Email Sending Failed: ", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (callback != null) {
            if (success) {
                callback.onSuccess();
            } else {
                callback.onFailure();
            }
        }
    }

    public interface MailCallback {
        void onSuccess();
        void onFailure();
    }
}
