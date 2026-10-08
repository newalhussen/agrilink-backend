package com.agrilink.integration;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal JSON-over-HTTP client for end-to-end tests against the running application. */
final class ApiClient {

    record Response(int status, JsonNode json, String raw) {
        JsonNode body() {
            return json;
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String base;
    private final String origin;

    ApiClient(int port) {
        this.origin = "http://localhost:" + port;
        this.base = origin + "/api/v1";
    }

    /** GET relative to the server root instead of /api/v1 (e.g. /api-docs). */
    Response rootGet(String path) {
        try {
            return toResponse(http.send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Response get(String path, String token) {
        return send("GET", path, token, null);
    }

    Response post(String path, String token, Object body) {
        return send("POST", path, token, body);
    }

    Response patch(String path, String token, Object body) {
        return send("PATCH", path, token, body);
    }

    Response put(String path, String token, Object body) {
        return send("PUT", path, token, body);
    }

    Response delete(String path, String token) {
        return send("DELETE", path, token, null);
    }

    Response send(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Accept", "application/json");
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else if (method.equals("GET") || method.equals("DELETE")) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString("{}"));
            }
            return toResponse(http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** multipart/form-data upload of one in-memory file plus text fields. */
    Response upload(String path, String token, String filename, String contentType, byte[] content,
                    Map<String, String> fields) {
        try {
            String boundary = "----agrilink" + System.nanoTime();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Map.Entry<String, String> f : fields.entrySet()) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n"
                        + f.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                    + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
            return toResponse(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Response toResponse(HttpResponse<String> response) {
        String raw = response.body();
        JsonNode json = null;
        if (raw != null && !raw.isBlank()) {
            try {
                json = mapper.readTree(raw);
            } catch (RuntimeException ex) {
                // Not JSON (e.g. image bytes); callers use raw()
            }
        }
        return new Response(response.statusCode(), json, raw);
    }
}
