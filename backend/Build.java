// Bygger backend utan Maven: bara JDK 25 och Spola-jarfilerna i lib/.
//
//   java Build.java              kompilerar och kör testerna
//   java Build.java compile      kompilerar bara appen, till out/main
//   java Build.java run [arg...] kompilerar och startar appen, t.ex. run --hash-password=ditt_lösenord
//   java Build.java fetch        hämtar Spola-jarfilerna som lib/SHA256SUMS listar
//   java Build.java upgrade 0.3.0 byter till en annan Spola-release och hämtar den
//
// Bara lib/SHA256SUMS är incheckad. Den säger vilka moduler och vilken version appen använder,
// och varje jar hämtas från Spolas GitHub-release och kontrolleras mot den innan bygget går vidare.
//
// Appen körs på modulvägen: java -p lib:out/main -m psykologen/se.olaslab.psykologen.PsykologenApplication

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

static final Path LIB = Path.of("lib");
static final Path SUMS = LIB.resolve("SHA256SUMS");
static final String RELEASES = "https://github.com/paokarlsson/spola/releases/download/v";
// En rad i SHA256SUMS: "<sha256>  spola.<modul>-<version>.jar".
static final Pattern SUM_LINE = Pattern.compile("([0-9a-f]{64})  (spola\\.[a-z.]+)-([0-9][^\\s]*)\\.jar");
static final Path MAIN_SOURCES = Path.of("src", "main", "java");
static final Path TEST_SOURCES = Path.of("src", "test", "java");
static final Path MAIN_CLASSES = Path.of("out", "main");
static final Path TEST_CLASSES = Path.of("out", "test");

static final String MODULE = "psykologen";
static final String MAIN_CLASS = "se.olaslab.psykologen.PsykologenApplication";
static final String TEST_CLASS = "se.olaslab.psykologen.AllTests";
// Testerna ligger i appens egna paket och läser testkörarens modul utan att appen kräver den.
static final List<String> TEST_RUNNER = List.of("--add-modules", "spola.test", "--add-reads", MODULE + "=spola.test");

void main(String[] args) throws Exception {
    var command = args.length == 0 ? "test" : args[0];
    if (!command.equals("upgrade")) {
        fetch();
    }
    switch (command) {
        case "test" -> {
            compileMain();
            compileTests();
            runTests();
        }
        case "compile" -> compileMain();
        case "fetch" -> { }
        case "upgrade" -> {
            if (args.length != 2) {
                fail("Ange versionen: java Build.java upgrade 0.3.0");
            }
            upgrade(args[1]);
            fetch();
        }
        case "run" -> {
            compileMain();
            run(List.of(args).subList(1, args.length));
        }
        default -> fail("Okänt kommando '" + command + "'. Använd test, compile, run, fetch eller upgrade.");
    }
}

// Hämtar de jar-filer som saknas eller inte stämmer med SHA256SUMS och tar bort andra
// Spola-jarfiler, så att modulvägen aldrig har två versioner av samma modul.
void fetch() throws Exception {
    var wanted = readSums(Files.readString(SUMS));
    for (var entry : wanted.entrySet()) {
        var jar = LIB.resolve(entry.getKey());
        if (Files.exists(jar) && sha256(Files.readAllBytes(jar)).equals(entry.getValue())) {
            continue;
        }
        var bytes = download(RELEASES + version(entry.getKey()) + "/" + entry.getKey());
        if (!sha256(bytes).equals(entry.getValue())) {
            fail(entry.getKey() + " stämmer inte med kontrollsumman i " + SUMS + ".");
        }
        var temp = Files.createTempFile(LIB, entry.getKey(), ".part");
        Files.write(temp, bytes);
        Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("Hämtade " + jar);
    }
    try (var files = Files.list(LIB)) {
        for (var jar : files.filter(file -> file.getFileName().toString().endsWith(".jar")).toList()) {
            if (!wanted.containsKey(jar.getFileName().toString())) {
                Files.delete(jar);
            }
        }
    }
}

// Skriver om SHA256SUMS till samma moduler från en annan release, med releasens kontrollsummor.
void upgrade(String version) throws Exception {
    var release = readSums(new String(download(RELEASES + version + "/SHA256SUMS")));
    var lines = new ArrayList<String>();
    for (var current : readSums(Files.readString(SUMS)).keySet()) {
        var jar = module(current) + "-" + version + ".jar";
        if (!release.containsKey(jar)) {
            fail("Spola " + version + " har ingen " + jar + ".");
        }
        lines.add(release.get(jar) + "  " + jar);
    }
    Files.write(SUMS, lines);
    System.out.println("Bytte till Spola " + version + " i " + SUMS);
}

// Jar-namn till kontrollsumma. Releasens SHA256SUMS har också källkods-jarfiler, och dem hoppar vi över.
Map<String, String> readSums(String text) {
    var sums = new LinkedHashMap<String, String>();
    for (var line : text.lines().map(String::strip).toList()) {
        if (line.isEmpty() || line.endsWith("-sources.jar")) {
            continue;
        }
        var match = SUM_LINE.matcher(line);
        if (match.matches()) {
            sums.put(match.group(2) + "-" + match.group(3) + ".jar", match.group(1));
        } else {
            fail("Kan inte läsa raden '" + line + "' i SHA256SUMS.");
        }
    }
    return sums;
}

String module(String jar) {
    return jar.substring(0, jar.indexOf('-'));
}

String version(String jar) {
    return jar.substring(jar.indexOf('-') + 1, jar.length() - ".jar".length());
}

byte[] download(String url) throws Exception {
    // Utan proxy(...) struntar HttpClient i https.proxyHost och liknande inställningar.
    try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault()).build()) {
        var response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            fail("Kunde inte hämta " + url + " (HTTP " + response.statusCode() + ").");
        }
        return response.body();
    }
}

String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
}

void compileMain() throws IOException {
    javac(sources(MAIN_SOURCES), MAIN_CLASSES, List.of());
    System.out.println("Kompilerade appen till " + MAIN_CLASSES);
}

void compileTests() throws IOException {
    var sources = new ArrayList<>(sources(MAIN_SOURCES));
    sources.addAll(sources(TEST_SOURCES));
    javac(sources, TEST_CLASSES, TEST_RUNNER);
    System.out.println("Kompilerade testerna till " + TEST_CLASSES);
}

void runTests() throws Exception {
    var command = new ArrayList<>(List.of(java(), "--module-path", modulePath(TEST_CLASSES)));
    command.addAll(TEST_RUNNER);
    command.addAll(List.of("--module", MODULE + "/" + TEST_CLASS));
    exec(command, "Testerna");
}

void run(List<String> appArgs) throws Exception {
    var command = new ArrayList<>(List.of(java(), "--module-path", modulePath(MAIN_CLASSES),
            "--module", MODULE + "/" + MAIN_CLASS));
    command.addAll(appArgs);
    exec(command, "Appen");
}

void javac(List<Path> sources, Path out, List<String> extra) throws IOException {
    delete(out);
    var args = new ArrayList<>(List.of("-encoding", "UTF-8", "--module-path", LIB.toString(),
            "-d", out.toString()));
    args.addAll(extra);
    sources.forEach(source -> args.add(source.toString()));
    var javac = ToolProvider.findFirst("javac").orElseThrow(() -> new IllegalStateException(
            "javac saknas. Kör med en JDK, inte en JRE."));
    if (javac.run(System.out, System.err, args.toArray(String[]::new)) != 0) {
        fail("Kompileringen misslyckades.");
    }
}

List<Path> sources(Path dir) throws IOException {
    try (var files = Files.walk(dir)) {
        return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
    }
}

String modulePath(Path classes) {
    return LIB + File.pathSeparator + classes;
}

String java() {
    return ProcessHandle.current().info().command().orElse("java");
}

void exec(List<String> command, String what) throws Exception {
    var exit = new ProcessBuilder(command).inheritIO().start().waitFor();
    if (exit != 0) {
        fail(what + " avslutades med kod " + exit + ".");
    }
}

void delete(Path dir) throws IOException {
    if (!Files.exists(dir)) {
        return;
    }
    try (var files = Files.walk(dir)) {
        for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
            Files.delete(file);
        }
    }
}

void fail(String message) {
    System.err.println("FEL: " + message);
    System.exit(1);
}
