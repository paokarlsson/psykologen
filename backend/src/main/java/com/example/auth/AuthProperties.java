package com.example.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;

/**
 * De konton som får logga in, lästa från konfigurationen (i praktiken
 * {@code backend/.env}, som redan är gitignorerad och används för
 * AI-nyckeln):
 *
 * <pre>
 * APP_AUTH_USERS_0_USERNAME=pao
 * APP_AUTH_USERS_0_PASSWORDHASH={bcrypt}$2a$10$...
 * </pre>
 *
 * Inga lösenord i klartext lagras någonstans - bara BCrypt-hashar, som
 * genereras med {@link PasswordHashRunner}.
 *
 * En vanlig klass utan {@code @ConfigurationProperties}: värdena läses med
 * {@link Environment#getProperty} i
 * {@link com.example.PsykologenApplication}, samma sätt som AI-nyckeln redan
 * läses där. Klassen känner därmed inte till Spring alls.
 *
 * {@link #validate()} körs vid uppstart. Att användarnamnet valideras hårt
 * ({@link #USERNAME_PATTERN}) är inte kosmetika: namnet används som
 * katalognamn för användarens egen lagring, så ett namn med {@code ../} i
 * hade betytt katalogtraversering. Genom att avvisa det vid uppstart kan det
 * aldrig nå {@code Path.resolve()}.
 */
public class AuthProperties {

    /** Bara gemener, siffror, bindestreck och understreck - se klassens javadoc. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

    private final List<User> users;

    public AuthProperties(List<User> users) {
        this.users = List.copyOf(users);
    }

    /**
     * Läser {@code APP_AUTH_USERS_<n>_USERNAME}/{@code _PASSWORDHASH} med
     * början på 0 och slutar vid första numret som saknar användarnamn.
     */
    public static AuthProperties fromEnvironment(Environment env) {
        List<User> found = new ArrayList<>();
        for (int i = 0;; i++) {
            String username = env.getProperty("APP_AUTH_USERS_" + i + "_USERNAME");
            if (username == null) {
                break;
            }
            found.add(new User(username, env.getProperty("APP_AUTH_USERS_" + i + "_PASSWORDHASH")));
        }
        return new AuthProperties(found);
    }

    public List<User> getUsers() {
        return users;
    }

    /** Kastar {@link InvalidUsersException} om kontona inte går att använda. */
    public void validate() {
        if (users.isEmpty()) {
            throw new InvalidUsersException("Inga konton är konfigurerade.");
        }

        Set<String> seen = new HashSet<>();
        for (User user : users) {
            String username = user.username();
            if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
                throw new InvalidUsersException("Ogiltigt användarnamn: '" + username
                        + "'. Tillåtet är 3-32 tecken med gemener, siffror, - och _.");
            }
            if (!seen.add(username)) {
                throw new InvalidUsersException("Användarnamnet '" + username + "' är angivet flera gånger.");
            }
            String hash = user.passwordHash();
            if (hash == null || !hash.startsWith("{bcrypt}$")) {
                throw new InvalidUsersException("Lösenordet för '" + username
                        + "' är ingen BCrypt-hash. Det ska börja med {bcrypt}$.");
            }
        }
    }

    public record User(String username, String passwordHash) {
    }
}
