package com.company;

import com.sun.net.httpserver.*;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Embedded HTTP server (localhost:8080) that hosts the Plaid Link UI.
 *
 * Flow:
 *  1. Java creates a link_token and passes it here.
 *  2. This server opens a browser tab at http://localhost:8080.
 *  3. The page initializes Plaid Link JS with that token.
 *  4. On success the page POSTs {public_token, institution_name} to /callback.
 *  5. openAndAwait() returns the public_token to the caller.
 *
 * For OAuth banks (Chase, BofA) the bank redirects to:
 *   http://localhost:8080/oauth-return
 * which re-opens Plaid Link to finish the handshake.
 */
public class PlaidLinkServer {

    public static final String REDIRECT_URI = "http://localhost:8080/oauth-return";
    private static final int PORT = 8080;

    private final HttpServer server;
    private final CountDownLatch done = new CountDownLatch(1);

    private volatile String publicToken;
    private volatile String institutionName;

    public PlaidLinkServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    /**
     * Starts the server, opens the browser, waits (up to 5 min) for the user
     * to finish the Link flow, then returns the public_token.
     *
     * @param linkToken link_token from Plaid /link/token/create
     */
    public String openAndAwait(String linkToken) throws Exception {
        // Serve the Link page
        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            sendHtml(exchange, buildLinkPage(linkToken, false));
        });

        // OAuth return — re-initialize Link to complete the OAuth handshake
        server.createContext("/oauth-return", exchange -> {
            // Pass the received_redirect_uri back into Plaid Link
            String receivedUri = "http://localhost:8080/oauth-return"
                    + (exchange.getRequestURI().getQuery() != null
                    ? "?" + exchange.getRequestURI().getQuery() : "");
            sendHtml(exchange, buildOAuthReturnPage(linkToken, receivedUri));
        });

        // Callback from the Link JS after success
        server.createContext("/callback", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            publicToken     = extractJson(body, "public_token");
            institutionName = extractJson(body, "institution_name");

            String resp = "{\"status\":\"ok\"}";
            sendJson(exchange, resp);
            done.countDown();
        });

        server.start();

        System.out.println("  Opening Plaid Link in your browser...");
        System.out.println("  (If browser does not open, navigate to http://localhost:" + PORT + ")");
        openBrowser("http://localhost:" + PORT);

        boolean completed = done.await(5, TimeUnit.MINUTES);
        server.stop(1);

        if (!completed || publicToken == null) {
            throw new RuntimeException("Plaid Link timed out or was cancelled.");
        }
        return publicToken;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    // ── HTML builders ────────────────────────────────────────────────────────

    private String buildLinkPage(String linkToken, boolean isOAuth) {
        String oauthLine = isOAuth ? "" :
                "receivedRedirectUri: window.location.href,";
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <title>Connect Your Card — Plaid Link</title>
                  <style>
                    body { font-family: -apple-system, sans-serif; display: flex;
                           justify-content: center; align-items: center;
                           height: 100vh; margin: 0; background: #f0f4f8; }
                    .card { background: #fff; border-radius: 12px; padding: 40px 48px;
                            box-shadow: 0 4px 20px rgba(0,0,0,.1); text-align: center; }
                    h2 { margin: 0 0 8px; color: #1a1a2e; }
                    p  { color: #555; margin: 0 0 24px; }
                    #status { color: #2d6a4f; font-weight: 600; min-height: 20px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>Credit Card Payment Tracker</h2>
                    <p>Connect your account securely via Plaid</p>
                    <div id="status">Opening bank connection…</div>
                  </div>
                  <script src="https://cdn.plaid.com/link/v2/stable/link-initialize.js"></script>
                  <script>
                    (function () {
                      var handler = Plaid.create({
                        token: '%s',
                        onSuccess: function (public_token, metadata) {
                          document.getElementById('status').textContent =
                            '✓ Connected to ' + metadata.institution.name + '. You may close this tab.';
                          fetch('/callback', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                              public_token: public_token,
                              institution_name: metadata.institution.name
                            })
                          });
                        },
                        onExit: function (err) {
                          if (err) {
                            document.getElementById('status').textContent =
                              'Error: ' + (err.display_message || err.error_message);
                          } else {
                            document.getElementById('status').textContent = 'Cancelled.';
                          }
                        }
                      });
                      handler.open();
                    })();
                  </script>
                </body>
                </html>
                """.formatted(linkToken);
    }

    private String buildOAuthReturnPage(String linkToken, String receivedUri) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>Completing connection…</title></head>
                <body>
                  <p>Completing bank connection…</p>
                  <script src="https://cdn.plaid.com/link/v2/stable/link-initialize.js"></script>
                  <script>
                    var handler = Plaid.create({
                      token: '%s',
                      receivedRedirectUri: '%s',
                      onSuccess: function (public_token, metadata) {
                        fetch('/callback', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                            public_token: public_token,
                            institution_name: metadata.institution.name
                          })
                        }).then(function() {
                          document.body.innerHTML = '<p>✓ Connected. You may close this tab.</p>';
                        });
                      },
                      onExit: function(err) {
                        document.body.innerHTML = '<p>Cancelled or error.</p>';
                      }
                    });
                    handler.open();
                  </script>
                </body>
                </html>
                """.formatted(linkToken, receivedUri);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) { /* user will open manually */ }
    }

    /** Minimal JSON string-value extractor — avoids an extra dependency. */
    private String extractJson(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start == -1) return null;
        start += needle.length();
        int end = json.indexOf('"', start);
        return end == -1 ? null : json.substring(start, end);
    }
}
