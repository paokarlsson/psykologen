// Bygger backend utan Maven: bara JDK 25 och Spola-jarfilerna i lib/.
//
//   java Build.java              kompilerar och kör testerna
//   java Build.java compile      kompilerar bara appen, till out/main
//   java Build.java run [arg...] kompilerar och startar appen, t.ex. run --hash-password=ditt_lösenord
//
// Appen körs på modulvägen: java -p lib:out/main -m psykologen/se.olaslab.psykologen.PsykologenApplication

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;

static final Path LIB = Path.of("lib");
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
    switch (command) {
        case "test" -> {
            compileMain();
            compileTests();
            runTests();
        }
        case "compile" -> compileMain();
        case "run" -> {
            compileMain();
            run(List.of(args).subList(1, args.length));
        }
        default -> fail("Okänt kommando '" + command + "'. Använd test, compile eller run.");
    }
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
