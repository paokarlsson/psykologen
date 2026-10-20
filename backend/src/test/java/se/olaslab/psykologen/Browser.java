package se.olaslab.psykologen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import spola.json.Json;
import spola.json.JsonObject;

/**
 * En webbläsare med Angulars beteende, för tester mot en riktig server: den sparar cookies och
 * skickar {@code XSRF-TOKEN} tillbaka i {@code X-XSRF-TOKEN} på varje anrop som ändrar något.
 */
public final class Browser {

    private final HttpClient client = HttpClient.newHttpClient();
    private final URI base;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public Browser(URI base) {
        this.base = base;
    }

    public Reply get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, false);
    }

    public Reply post(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, json, true);
    }

    public Reply put(String path, String json) throws IOException, InterruptedException {
        return send("PUT", path, json, true);
    }

    /** En POST som en sida från en annan webbplats kan få webbläsaren att skicka: utan CSRF-huvudet. */
    public Reply postWithoutCsrf(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, json, false);
    }

    public Map<String, String> cookies() {
        return Map.copyOf(cookies);
    }

    public Reply login(String username, String password) throws IOException, InterruptedException {
        return post("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private Reply send(String method, String path, String json, boolean csrf)
            throws IOException, InterruptedException {
        if (csrf && !cookies.containsKey("XSRF-TOKEN")) {
            // Som Angular: appen frågar /me när den startar och får cookien på svaret.
            get("/api/auth/me");
        }
        var request = HttpRequest.newBuilder(base.resolve(path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (csrf && cookies.containsKey("XSRF-TOKEN")) {
            request.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").forEach(this::store);
        return new Reply(response.statusCode(), response.headers().allValues("Set-Cookie"), response.body());
    }

    private void store(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        int equals = pair.indexOf('=');
        String name = pair.substring(0, equals).strip();
        String value = pair.substring(equals + 1).strip();
        if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
            cookies.remove(name);
        } else {
            cookies.put(name, value);
        }
    }

    public record Reply(int status, List<String> setCookies, String body) {

        public JsonObject json() {
            return (JsonObject) Json.parse(body);
        }
    }
}
