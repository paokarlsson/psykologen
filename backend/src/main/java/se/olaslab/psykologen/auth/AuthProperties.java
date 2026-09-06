package se.olaslab.psykologen.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;

public class AuthProperties {

    /** Namnet blir katalognamn för användarens lagring - inga tecken som tillåter katalogtraversering. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

    private final List<User> users;

    public AuthProperties(List<User> users) {
        this.users = List.copyOf(users);
    }

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
