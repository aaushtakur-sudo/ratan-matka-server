import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URLDecoder;

public class NoticeServer {
    static final String DIR = System.getProperty("user.dir");
    static final String FILE = DIR + "/results.json";

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static String readBody(HttpExchange e) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int n; while ((n = e.getRequestBody().read(buffer)) != -1) { out.write(buffer, 0, n); }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange e, int code, String type, String body)
            throws IOException {
        e.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        e.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        e.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        e.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(code, data.length);
        e.getResponseBody().write(data);
        e.close();
    }

    static String field(String body, String key) throws Exception {
        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 &&
                URLDecoder.decode(pair[0], "UTF-8").equals(key)) {
                return URLDecoder.decode(pair[1], "UTF-8");
            }
        }
        return "";
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(DIR));

        if (!Files.exists(Paths.get(FILE))) {
            Files.write(Paths.get(FILE),
                "{\"today\":\"अभी कोई सूचना प्रकाशित नहीं हुई है।\",\"history\":[]}"
                .getBytes(StandardCharsets.UTF_8));
        }

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"))), 0);

        server.createContext("/api/results", e -> {
            try {
                if ("OPTIONS".equals(e.getRequestMethod())) {
                    send(e, 204, "text/plain", "");
                    return;
                }
                send(e, 200, "application/json",
                    new String(Files.readAllBytes(Paths.get(FILE)),
                    StandardCharsets.UTF_8));
            } catch (Exception ex) {
                try { send(e, 500, "text/plain", "Server error"); }
                catch (Exception ignored) {}
            }
        });

        server.createContext("/api/notice", e -> {
            try {
                if ("OPTIONS".equals(e.getRequestMethod())) {
                    send(e, 204, "text/plain", "");
                    return;
                }
                if (!"POST".equals(e.getRequestMethod())) {
                    send(e, 405, "text/plain", "POST required");
                    return;
                }

                String body = readBody(e);
                String title = field(body, "title");
                String date = field(body, "date");
                String details = field(body, "details");

                if (title.isEmpty() || date.isEmpty() || details.isEmpty()) {
                    send(e, 400, "application/json",
                        "{\"ok\":false,\"error\":\"तीनों फ़ील्ड भरें\"}");
                    return;
                }

                String old = new String(Files.readAllBytes(Paths.get(FILE)),
                    StandardCharsets.UTF_8);

                String item = "{\"title\":\"" + escape(title) +
                    "\",\"date\":\"" + escape(date) +
                    "\",\"details\":\"" + escape(details) + "\"}";

                String history = "";
                int start = old.indexOf("\"history\"");
                int open = start < 0 ? -1 : old.indexOf('[', start);
                int close = open < 0 ? -1 : old.indexOf(']', open);

                if (open >= 0 && close >= 0) {
                    history = old.substring(open + 1, close).trim();
                }

                String newHistory = item +
                    (history.isEmpty() ? "" : "," + history);

                String result = "{\"today\":\"" + escape(title) +
                    "\",\"history\":[" + newHistory + "]}";

                Files.write(Paths.get(FILE),
                    result.getBytes(StandardCharsets.UTF_8));

                send(e, 200, "application/json", "{\"ok\":true}");
            } catch (Exception ex) {
                try { send(e, 500, "application/json",
                    "{\"ok\":false,\"error\":\"Server error\"}"); }
                catch (Exception ignored) {}
            }
        });

        server.createContext("/", e -> {
            try {
                String path = e.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";

                Path root = Paths.get(DIR).toRealPath();
                Path file = root.resolve(path.substring(1)).normalize();

                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    send(e, 404, "text/plain", "Not found");
                    return;
                }

                String type = path.endsWith(".html") ? "text/html" :
                    path.endsWith(".json") ? "application/json" :
                    "text/plain";

                send(e, 200, type,
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                try { send(e, 500, "text/plain", "Server error"); }
                catch (Exception ignored) {}
            }
        });

        server.start();
        System.out.println("Notice server started on port " + server.getAddress().getPort());
        System.out.println("Open http://127.0.0.1:8081/");
    }
}
