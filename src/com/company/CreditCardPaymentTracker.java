package com.company;

import com.plaid.client.ApiClient;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import retrofit2.Response;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Credit Card Payment Tracker — powered by Plaid Liabilities API.
 *
 * Usage:
 *   java -jar PrepJava.jar [add]      → connect a new bank account
 *   java -jar PrepJava.jar            → show next-month payment schedule
 *   java -jar PrepJava.jar list       → list connected accounts
 *   java -jar PrepJava.jar remove     → remove a connected account
 *
 * Supported institutions: Chase, Capital One, American Express, Bilt,
 *                         Bank of America (and any other Plaid-supported card).
 *
 * The app fetches: statement balance, minimum payment, and next due date
 * via the /liabilities/get endpoint. Add Products.TRANSACTIONS to link
 * token creation when you need full transaction history.
 */
public class CreditCardPaymentTracker {

    // ── Known target institutions (display names) ─────────────────────────────
    private static final List<String> TARGET_INSTITUTIONS = List.of(
            "Chase", "Capital One", "American Express", "Bilt", "Bank of America"
    );

    private final PlaidApi plaid;
    private final PlaidTokenStore tokenStore;
    private final PlaidConfig config;

    public CreditCardPaymentTracker(PlaidConfig config) {
        this.config     = config;
        this.tokenStore = new PlaidTokenStore();

        ApiClient apiClient = new ApiClient();
        switch (config.environment.toLowerCase()) {
            case "sandbox"     -> apiClient.setPlaidAdapter(ApiClient.Sandbox);
            case "development" -> apiClient.setPlaidAdapter(ApiClient.Development);
            default            -> apiClient.setPlaidAdapter(ApiClient.Production);
        }
        apiClient.setApiKey("client-id", config.clientId);
        apiClient.setApiKey("secret",    config.secret);
        this.plaid = apiClient.createService(PlaidApi.class);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        PlaidConfig config  = PlaidConfig.load();
        CreditCardPaymentTracker tracker = new CreditCardPaymentTracker(config);

        String command = args.length > 0 ? args[0].toLowerCase() : "show";

        switch (command) {
            case "add"    -> tracker.addAccount();
            case "list"   -> tracker.listAccounts();
            case "remove" -> tracker.removeAccount();
            default       -> tracker.showPaymentSchedule();
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    /** Run Plaid Link for each un-connected target institution. */
    private void addAccount() throws Exception {
        System.out.println("\nWhich institution would you like to connect?");
        System.out.println("  Known cards: " + String.join(", ", TARGET_INSTITUTIONS));
        System.out.println("  (Enter any other institution name too)\n");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Institution name: ");
        String institution = scanner.nextLine().trim();
        if (institution.isEmpty()) {
            System.out.println("No institution entered, exiting.");
            return;
        }

        System.out.println("\nStarting Plaid Link for: " + institution);
        String linkToken = createLinkToken();

        PlaidLinkServer linkServer = new PlaidLinkServer();
        String publicToken = linkServer.openAndAwait(linkToken);
        String detectedName = linkServer.getInstitutionName();

        // Prefer the name Plaid returned (matches the institution's official name)
        String finalName = (detectedName != null && !detectedName.isBlank())
                ? detectedName : institution;

        System.out.println("\nExchanging public token for " + finalName + "...");
        ItemPublicTokenExchangeRequest req = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);
        Response<ItemPublicTokenExchangeResponse> resp =
                plaid.itemPublicTokenExchange(req).execute();

        requireSuccess(resp);
        String accessToken = resp.body().getAccessToken();
        String itemId      = resp.body().getItemId();

        tokenStore.upsert(finalName, accessToken, itemId);
        System.out.println("  Connected: " + finalName);
        System.out.println("\nRun without arguments to see your payment schedule.");
    }

    /** Print the payment schedule for next month across all connected cards. */
    private void showPaymentSchedule() throws Exception {
        List<PlaidTokenStore.TokenEntry> tokens = tokenStore.loadAll();
        if (tokens.isEmpty()) {
            System.out.println("\nNo accounts connected yet. Run with 'add' to connect your first card.");
            return;
        }

        List<CardPayment> payments = new ArrayList<>();

        for (PlaidTokenStore.TokenEntry entry : tokens) {
            System.out.println("Fetching liabilities for " + entry.institution + "...");
            try {
                List<CardPayment> cards = fetchLiabilities(entry);
                payments.addAll(cards);
            } catch (Exception e) {
                System.err.println("  Warning: could not fetch data for "
                        + entry.institution + " — " + e.getMessage());
            }
        }

        if (payments.isEmpty()) {
            System.out.println("\nNo credit card liabilities found.");
            return;
        }

        // Sort by next payment due date (nulls last)
        payments.sort(Comparator.comparing(
                p -> p.nextPaymentDueDate != null ? p.nextPaymentDueDate : LocalDate.MAX));

        printSchedule(payments);
    }

    private void listAccounts() {
        List<PlaidTokenStore.TokenEntry> tokens = tokenStore.loadAll();
        if (tokens.isEmpty()) {
            System.out.println("No accounts connected.");
            return;
        }
        System.out.println("\nConnected accounts:");
        tokens.forEach(t -> System.out.println("  • " + t.institution));
    }

    private void removeAccount() {
        List<PlaidTokenStore.TokenEntry> tokens = tokenStore.loadAll();
        if (tokens.isEmpty()) {
            System.out.println("No accounts connected.");
            return;
        }
        System.out.println("\nConnected accounts:");
        for (int i = 0; i < tokens.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, tokens.get(i).institution);
        }
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter number to remove (0 to cancel): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());
            if (choice < 1 || choice > tokens.size()) {
                System.out.println("Cancelled.");
                return;
            }
            String name = tokens.get(choice - 1).institution;
            tokenStore.remove(name);
            System.out.println("Removed: " + name);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    // ── Plaid API calls ───────────────────────────────────────────────────────

    private String createLinkToken() throws Exception {
        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId("default-user"))
                .clientName("Credit Card Payment Tracker")
                .products(List.of(Products.LIABILITIES))
                // To also enable transactions in a future session, add:
                //   Products.TRANSACTIONS
                .countryCodes(List.of(CountryCode.US))
                .language("en")
                // Required for OAuth banks (Chase, BofA)
                .redirectUri(PlaidLinkServer.REDIRECT_URI);

        Response<LinkTokenCreateResponse> resp =
                plaid.linkTokenCreate(request).execute();
        requireSuccess(resp);
        return resp.body().getLinkToken();
    }

    private List<CardPayment> fetchLiabilities(PlaidTokenStore.TokenEntry entry) throws Exception {
        LiabilitiesGetRequest req = new LiabilitiesGetRequest()
                .accessToken(entry.accessToken);
        Response<LiabilitiesGetResponse> resp = plaid.liabilitiesGet(req).execute();
        requireSuccess(resp);

        LiabilitiesObject liabilities = resp.body().getLiabilities();
        List<AccountBase> accounts    = resp.body().getAccounts();

        // Build account-id → account-name lookup
        Map<String, String> accountNames = new HashMap<>();
        if (accounts != null) {
            for (AccountBase acc : accounts) {
                String label = acc.getName()
                        + (acc.getMask() != null ? " (…" + acc.getMask() + ")" : "");
                accountNames.put(acc.getAccountId(), label);
            }
        }

        List<CardPayment> result = new ArrayList<>();
        if (liabilities == null || liabilities.getCredit() == null) return result;

        for (CreditCardLiability card : liabilities.getCredit()) {
            String accountLabel = accountNames.getOrDefault(
                    card.getAccountId(), entry.institution);

            result.add(new CardPayment(
                    entry.institution + " — " + accountLabel,
                    card.getLastStatementBalance(),
                    card.getMinimumPaymentAmount(),
                    card.getNextPaymentDueDate()
            ));
        }
        return result;
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private void printSchedule(List<CardPayment> payments) {
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.printf ("║   Payments Due — %-55s║%n",
                nextMonth.getMonth() + " " + nextMonth.getYear());
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  %-30s  %-13s  %-10s  %-10s║%n",
                "Account", "Statement Bal", "Min Due", "Due Date");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");

        double totalBalance = 0;
        double totalMin     = 0;

        for (CardPayment p : payments) {
            String balStr  = p.statementBalance  != null ? String.format("$%,.2f", p.statementBalance)  : "—";
            String minStr  = p.minimumPayment    != null ? String.format("$%,.2f", p.minimumPayment)    : "—";
            String dateStr = p.nextPaymentDueDate != null ? dateFmt.format(p.nextPaymentDueDate)        : "—";

            // Highlight cards due within 7 days
            String flag = "";
            if (p.nextPaymentDueDate != null) {
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), p.nextPaymentDueDate);
                if (daysUntil >= 0 && daysUntil <= 7) flag = " ⚠";
            }

            System.out.printf("║  %-30s  %-13s  %-10s  %-10s║%n",
                    truncate(p.accountName, 30),
                    balStr, minStr, dateStr + flag);

            if (p.statementBalance != null) totalBalance += p.statementBalance;
            if (p.minimumPayment   != null) totalMin     += p.minimumPayment;
        }

        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  %-30s  %-13s  %-10s  %-10s║%n",
                "TOTAL",
                String.format("$%,.2f", totalBalance),
                String.format("$%,.2f", totalMin),
                "");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println("  ⚠ = due within 7 days");
        System.out.println("  Statement balance = full amount; Min Due = minimum payment required");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static <T> void requireSuccess(Response<T> resp) throws Exception {
        if (!resp.isSuccessful() || resp.body() == null) {
            String error = resp.errorBody() != null ? resp.errorBody().string() : "(no body)";
            throw new RuntimeException("Plaid API error " + resp.code() + ": " + error);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    // ── Data class ────────────────────────────────────────────────────────────

    static class CardPayment {
        final String    accountName;
        final Double    statementBalance;   // last_statement_balance
        final Double    minimumPayment;     // minimum_payment_amount
        final LocalDate nextPaymentDueDate; // next_payment_due_date

        CardPayment(String accountName, Double statementBalance,
                    Double minimumPayment, LocalDate nextPaymentDueDate) {
            this.accountName       = accountName;
            this.statementBalance  = statementBalance;
            this.minimumPayment    = minimumPayment;
            this.nextPaymentDueDate = nextPaymentDueDate;
        }
    }
}
