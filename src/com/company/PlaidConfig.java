package com.company;

import java.io.*;
import java.util.Properties;

/**
 * Loads Plaid credentials from environment variables or
 * ~/.creditcard-tracker/plaid.properties.
 *
 * Required fields:
 *   PLAID_CLIENT_ID  — your Plaid client_id
 *   PLAID_SECRET     — your Plaid secret (use Development or Production)
 *   PLAID_ENV        — sandbox | development | production  (default: production)
 *
 * For OAuth banks (Chase, Bank of America) you must also register
 * http://localhost:8080/oauth-return in your Plaid dashboard under
 * API > Allowed redirect URIs.
 */
public class PlaidConfig {

    public final String clientId;
    public final String secret;
    public final String environment;

    private PlaidConfig(String clientId, String secret, String environment) {
        this.clientId = clientId;
        this.secret = secret;
        this.environment = environment;
    }

    public static PlaidConfig load() {
        // 1. Try environment variables
        String clientId   = System.getenv("PLAID_CLIENT_ID");
        String secret     = System.getenv("PLAID_SECRET");
        String environment = System.getenv("PLAID_ENV");

        // 2. Fall back to ~/.creditcard-tracker/plaid.properties
        if (clientId == null) {
            File propsFile = configFile();
            if (propsFile.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(propsFile)) {
                    props.load(fis);
                    clientId    = props.getProperty("client_id");
                    secret      = props.getProperty("secret");
                    environment = props.getProperty("environment", "production");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read " + propsFile, e);
                }
            }
        }

        if (clientId == null || secret == null) {
            printSetupInstructions();
            System.exit(1);
        }

        return new PlaidConfig(clientId, secret,
                environment != null ? environment : "production");
    }

    public static File configFile() {
        return new File(trackerDir(), "plaid.properties");
    }

    public static File trackerDir() {
        File dir = new File(System.getProperty("user.home"), ".creditcard-tracker");
        dir.mkdirs();
        return dir;
    }

    private static void printSetupInstructions() {
        System.err.println("""
                ╔══════════════════════════════════════════════════════════╗
                ║  Plaid credentials not configured                        ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Option A — environment variables:                       ║
                ║    export PLAID_CLIENT_ID=your_client_id                 ║
                ║    export PLAID_SECRET=your_secret                       ║
                ║    export PLAID_ENV=development   # or production        ║
                ║                                                          ║
                ║  Option B — properties file:                             ║
                ║    ~/.creditcard-tracker/plaid.properties                ║
                ║      client_id=your_client_id                            ║
                ║      secret=your_secret                                  ║
                ║      environment=development                             ║
                ║                                                          ║
                ║  Get credentials at https://dashboard.plaid.com          ║
                ║  Enable products: Liabilities (+ Transactions later)     ║
                ║  Register redirect URI for OAuth banks:                  ║
                ║    http://localhost:8080/oauth-return                    ║
                ╚══════════════════════════════════════════════════════════╝
                """);
    }
}
