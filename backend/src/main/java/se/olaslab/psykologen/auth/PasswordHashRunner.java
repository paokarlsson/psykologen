package se.olaslab.psykologen.auth;

import java.io.PrintStream;
import java.util.Optional;

import spola.security.PasswordHasher;

/**
 * Skriver ut en lösenordshash till {@code backend/.env}:
 *
 * <pre>
 * java Build.java run --hash-password=ditt_lösenord
 * </pre>
 *
 * <p>Appen startar då inte, så inga konton eller API-nycklar behövs.
 */
public final class PasswordHashRunner {

    static final String OPTION = "--hash-password";

    private PasswordHashRunner() {
    }

    /** Lösenordet när appen startades med {@code --hash-password}, annars tomt. */
    public static Optional<String> requestedPassword(String[] args) {
        for (String arg : args) {
            if (arg.equals(OPTION)) {
                return Optional.of("");
            }
            if (arg.startsWith(OPTION + "=")) {
                return Optional.of(arg.substring(OPTION.length() + 1));
            }
        }
        return Optional.empty();
    }

    public static void print(String password, PasswordHasher hasher, PrintStream out) {
        if (password.isBlank()) {
            out.println("Ange ett lösenord: --hash-password=ditt_lösenord");
            return;
        }

        String hash;
        try {
            hash = hasher.hash(password);
        } catch (IllegalArgumentException e) {
            // BCrypt läser bara 72 byte, och längre lösenord avvisas hellre än kortas av i tysthet.
            out.println("Lösenordet får vara högst 72 byte (UTF-8). Välj ett kortare.");
            return;
        }

        out.println();
        out.println("Klistra in raderna nedan i backend/.env (byt 0 mot nästa lediga nummer");
        out.println("om du redan har konton där, och ditt_användarnamn mot det du vill ha):");
        out.println();
        out.println("APP_AUTH_USERS_0_USERNAME=ditt_användarnamn");
        out.println("APP_AUTH_USERS_0_PASSWORDHASH=" + hash);
        out.println();
    }
}
