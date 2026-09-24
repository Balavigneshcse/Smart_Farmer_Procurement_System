package com.cropcoders.procurement.email;

/** The email provider refused or could not be reached. */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
