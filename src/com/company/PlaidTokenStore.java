package com.company;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Persists Plaid access tokens to ~/.creditcard-tracker/tokens.json.
 * Each entry maps a human-readable institution name to its access token.
 */
public class PlaidTokenStore {

    public static class TokenEntry {
        public String institution;   // e.g. "Chase"
        public String accessToken;   // e.g. "access-production-..."
        public String itemId;        // Plaid item_id

        public TokenEntry(String institution, String accessToken, String itemId) {
            this.institution = institution;
            this.accessToken = accessToken;
            this.itemId      = itemId;
        }
    }

    private final File storeFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PlaidTokenStore() {
        this.storeFile = new File(PlaidConfig.trackerDir(), "tokens.json");
    }

    public List<TokenEntry> loadAll() {
        if (!storeFile.exists()) return new ArrayList<>();
        try (FileReader reader = new FileReader(storeFile)) {
            Type listType = new TypeToken<List<TokenEntry>>() {}.getType();
            List<TokenEntry> entries = gson.fromJson(reader, listType);
            return entries != null ? entries : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Warning: could not read token store — " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Add or replace the token for a given institution. */
    public void upsert(String institution, String accessToken, String itemId) {
        List<TokenEntry> entries = loadAll();
        entries.removeIf(e -> e.institution.equalsIgnoreCase(institution));
        entries.add(new TokenEntry(institution, accessToken, itemId));
        save(entries);
        System.out.println("  Saved token for " + institution);
    }

    /** Remove token for an institution (e.g. if item becomes invalid). */
    public void remove(String institution) {
        List<TokenEntry> entries = loadAll();
        entries.removeIf(e -> e.institution.equalsIgnoreCase(institution));
        save(entries);
    }

    public boolean hasToken(String institution) {
        return loadAll().stream()
                .anyMatch(e -> e.institution.equalsIgnoreCase(institution));
    }

    private void save(List<TokenEntry> entries) {
        try (FileWriter writer = new FileWriter(storeFile)) {
            gson.toJson(entries, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save tokens to " + storeFile, e);
        }
    }
}
