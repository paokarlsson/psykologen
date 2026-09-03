package com.example.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * {@link #validate()} körs vid uppstart. Att användarnamnet valideras hårt
 * ({@link #USERNAME_PATTERN}) är inte kosmetika: namnet används som
 * katalognamn för användarens egen lagring, så ett namn med {@code ../} i
 * hade betytt katalogtraversering. Genom att avvisa det vid uppstart kan det
 * aldrig nå {@code Path.resolve()}.
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Bara gemener, siffror, bindestreck och understreck - se klassens javadoc. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

    private List<User> users = new ArrayList<>();

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    /** Kastar {@link InvalidUsersException} om kontona inte går att använda. */
    public void validate() {
        if (users.isEmpty()) {
            throw new InvalidUsersException("Inga konton är konfigurerade.");
        }

        Set<String> seen = new HashSet<>();
        for (User user : users) {
            String username = user.getUsername();
            if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
                throw new InvalidUsersException("Ogiltigt användarnamn: '" + username
                        + "'. Tillåtet är 3-32 tecken med gemener, siffror, - och _.");
            }
            if (!seen.add(username)) {
                throw new InvalidUsersException("Användarnamnet '" + username + "' är angivet flera gånger.");
            }
            String hash = user.getPasswordHash();
            if (hash == null || !hash.startsWith("{bcrypt}$")) {
                throw new InvalidUsersException("Lösenordet för '" + username
                        + "' är ingen BCrypt-hash. Det ska börja med {bcrypt}$.");
            }
        }
    }

    public static class User {

        private String username;
        private String passwordHash;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}
