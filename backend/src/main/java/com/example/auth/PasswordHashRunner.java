package com.example.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Litet hjälpmedel för att skapa lösenordshashar till {@code backend/.env}:
 *
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.arguments=--hash-password=ditt_lösenord
 * </pre>
 *
 * En {@link ApplicationRunner} i stället för ett eget {@code main} eller en
 * ny Maven-plugin: den får rätt {@link PasswordEncoder} injicerad, så hashen
 * garanterat skapas med samma inställningar som inloggningen sedan verifierar
 * mot.
 *
 * Notera att lösenordet syns i kommandoraden och därmed i skalets historik.
 * För ett labbprojekt är det acceptabelt; radera raden ur historiken om
 * lösenordet används någon annanstans.
 */
@Component
public class PasswordHashRunner implements ApplicationRunner {

    static final String OPTION = "hash-password";

    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;

    public PasswordHashRunner(PasswordEncoder passwordEncoder, ApplicationContext applicationContext) {
        this.passwordEncoder = passwordEncoder;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }

        String password = args.getOptionValues(OPTION).stream().findFirst().orElse("");
        if (password.isBlank()) {
            System.out.println("Ange ett lösenord: --hash-password=ditt_lösenord");
        } else {
            System.out.println();
            System.out.println("Klistra in raderna nedan i backend/.env (byt 0 mot nästa lediga nummer");
            System.out.println("om du redan har konton där, och ditt_användarnamn mot det du vill ha):");
            System.out.println();
            System.out.println("APP_AUTH_USERS_0_USERNAME=ditt_användarnamn");
            System.out.println("APP_AUTH_USERS_0_PASSWORDHASH=" + passwordEncoder.encode(password));
            System.out.println();
        }

        // Appen startades bara för att skapa en hash - lämna inte en server
        // igång som ändå inte har några konton att logga in med.
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
