package com.cropcoders.procurement.email;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.cropcoders.procurement.config.AppProperties;

/**
 * Builds every email the app sends, in the farmer's language (texts from messages*.properties).
 * The HTML uses simple tables and inline styles because many mail apps ignore stylesheets.
 * Every piece of text is HTML-escaped, so a name like "<b>Ravi</b>" is shown as typed.
 */
@Component
public class EmailTemplates {

    /** Size of the QR code in the email, in CSS pixels. */
    private static final int QR_SIZE = 180;

    private final MessageSource messages;
    private final String appUrl;

    public EmailTemplates(MessageSource messages, AppProperties properties) {
        this.messages = messages;
        String url = properties.email().appUrl();
        this.appUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The login OTP: the code is shown in large digits. */
    public EmailMessage otp(String to, String language, String code, int validMinutes) {
        Locale locale = Locale.forLanguageTag(language);
        return render(new Content(to, locale,
                text("notify.otp.subject", locale, code),
                null,
                text("notify.otp.text", locale, code, validMinutes),
                code,
                null,
                null,
                text("notify.otp.footer", locale),
                null,
                null,
                null));
    }

    /**
     * A notification (subject and text already in the farmer's language) with a button
     * that opens a page of the app, e.g. "/farmer/booking.html?id=12".
     */
    public EmailMessage notification(String to, String language, String farmerName, String subject, String message,
                                     String path) {
        return notification(to, language, farmerName, subject, message, path, null, null);
    }

    /**
     * The same, with the booking's QR code shown in the email (qrText = what the operator's scanner reads;
     * qrUrl = a signed link to the same picture, used by senders without inline pictures).
     */
    public EmailMessage notification(String to, String language, String farmerName, String subject, String message,
                                     String path, String qrText, String qrUrl) {
        Locale locale = Locale.forLanguageTag(language);
        return render(new Content(to, locale,
                subject,
                text("notify.greeting", locale, farmerName),
                message,
                null,
                text("notify.button", locale),
                appUrl + path,
                text("notify.footer", locale),
                qrText == null ? null : QrCode.png(qrText, QR_SIZE * 2),   // double size: sharp on phone screens
                qrText == null ? null : text("notify.qr", locale),
                qrUrl));
    }

    /** Everything one email shows. greeting, highlight and button are optional (null). */
    private record Content(String to, Locale locale, String subject, String greeting, String message,
                           String highlight, String buttonLabel, String buttonUrl, String footer,
                           byte[] qrPng, String qrCaption, String qrUrl) {
    }

    private EmailMessage render(Content c) {
        String appName = text("notify.app-name", c.locale());

        StringBuilder html = new StringBuilder(2048);
        html.append("<!DOCTYPE html><html lang=\"").append(c.locale().getLanguage()).append("\"><head>")
                .append("<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(c.subject())).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#f3f5f1;color:#17231b;")
                .append("font-family:Arial,'Noto Sans Tamil','Noto Sans Devanagari',sans-serif;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f3f5f1;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:520px;background:#ffffff;border:1px solid #d5ddd4;border-radius:12px;\">")
                // header bar with the app name
                .append("<tr><td style=\"background:#1d6a40;color:#ffffff;padding:16px 24px;font-size:16px;")
                .append("font-weight:bold;border-radius:12px 12px 0 0;\">").append(esc(appName)).append("</td></tr>")
                .append("<tr><td style=\"padding:24px;\">")
                .append("<h1 style=\"margin:0 0 16px;font-size:20px;line-height:1.35;color:#17231b;\">")
                .append(esc(c.subject())).append("</h1>");
        if (c.greeting() != null) {
            html.append(paragraph(c.greeting()));
        }
        html.append(paragraph(c.message()));
        if (c.highlight() != null) {
            html.append("<p style=\"margin:8px 0 16px;padding:14px;background:#e1efe5;border-radius:8px;")
                    .append("text-align:center;font-size:32px;font-weight:bold;letter-spacing:8px;color:#17231b;\">")
                    .append(esc(c.highlight())).append("</p>");
        }
        if (c.qrPng() != null) {
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 4px;\"><tr>")
                    .append("<td style=\"padding:10px;background:#ffffff;border:1px solid #d5ddd4;border-radius:8px;\">")
                    .append("<img src=\"cid:").append(EmailMessage.QR_CONTENT_ID).append("\" width=\"").append(QR_SIZE)
                    .append("\" height=\"").append(QR_SIZE).append("\" alt=\"").append(esc(c.qrCaption()))
                    .append("\" style=\"display:block;border:0;\"></td></tr></table>")
                    .append("<p style=\"margin:0 0 8px;font-size:13px;color:#4f5e54;\">").append(esc(c.qrCaption())).append("</p>");
        }
        if (c.buttonUrl() != null) {
            html.append("<p style=\"margin:24px 0 0;\"><a href=\"").append(esc(c.buttonUrl())).append("\" ")
                    .append("style=\"display:inline-block;background:#1d6a40;color:#ffffff;text-decoration:none;")
                    .append("padding:12px 22px;border-radius:8px;font-weight:bold;font-size:15px;\">")
                    .append(esc(c.buttonLabel())).append("</a></p>");
        }
        html.append("</td></tr>")
                .append("<tr><td style=\"padding:16px 24px;background:#f7faf7;color:#4f5e54;font-size:12px;")
                .append("line-height:1.5;border-radius:0 0 12px 12px;\">").append(esc(c.footer())).append("</td></tr>")
                .append("</table></td></tr></table></body></html>");

        StringBuilder text = new StringBuilder(512).append(appName).append("\n\n");
        if (c.greeting() != null) {
            text.append(c.greeting()).append("\n\n");
        }
        text.append(c.message()).append("\n\n");
        if (c.highlight() != null) {
            text.append(c.highlight()).append("\n\n");
        }
        if (c.buttonUrl() != null) {
            text.append(c.buttonLabel()).append(": ").append(c.buttonUrl()).append("\n\n");
        }
        text.append("--\n").append(c.footer()).append('\n');

        return new EmailMessage(c.to(), c.subject(), html.toString(), text.toString(), c.qrPng(), c.qrUrl());
    }

    private static String paragraph(String value) {
        return "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.6;\">" + esc(value) + "</p>";
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
