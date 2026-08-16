package com.taut0logy.jmeet.mail;

import com.taut0logy.jmeet.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class Mailer {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;

    public Mailer(JavaMailSender mailSender, TemplateEngine templateEngine, MailProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void send(EmailMessage email) throws Exception {
        Context context = new Context();
        context.setVariables(email.model());
        String html = templateEngine.process("mail/" + email.template(), context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(html, true);
        mailSender.send(mimeMessage);
    }
}
