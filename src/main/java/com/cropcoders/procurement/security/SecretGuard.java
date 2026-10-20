package com.cropcoders.procurement.security;

import java.util.regex.Pattern;

/**
 * Stops the app from starting on a server with the development secrets written in application.yml.
 * Those values are in the public code: with them anyone could sign their own login token (even as
 * super admin) or read the encrypted bank details. On the PC (http://localhost) they are fine.
 */
public final class SecretGuard {

    /** Every development default in application.yml starts with this. */
    private static final String DEV_PREFIX = "local-dev-";

    /** http://localhost or http://127.0.0.1 with any port and path ("http://localhost.example.com" is not this PC). */
    private static final Pattern LOCAL_URL = Pattern.compile("^http://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/.*)?$");

    private SecretGuard() {
    }

    /**
     * @param secret  the secret the app is using
     * @param envName the environment variable that sets it (named in the error message)
     * @param appUrl  app.email.app-url (APP_URL): the address the app runs at
     * @throws IllegalStateException if it is the development value and the app is not running on this PC
     */
    public static void requireRealSecret(String secret, String envName, String appUrl) {
        if (secret != null && secret.startsWith(DEV_PREFIX) && !isLocal(appUrl)) {
            throw new IllegalStateException(envName + " is not set. The app runs at " + appUrl
                    + " but uses the development value from application.yml, which everyone can read. Set "
                    + envName + " (32 or more random characters) in .env or as an environment variable, then restart.");
        }
    }

    /** The app runs on this PC (local development): http://localhost or http://127.0.0.1, with any port and path. */
    static boolean isLocal(String appUrl) {
        return appUrl != null && LOCAL_URL.matcher(appUrl).matches();
    }
}
