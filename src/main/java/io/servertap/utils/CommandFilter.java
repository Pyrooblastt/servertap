package io.servertap.utils;

import io.servertap.ServerTapMain;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Centralized allowlist filter for commands submitted through ServerTap's
 * external command execution routes (REST /v1/server/exec and the console
 * WebSocket).
 * <p>
 * This filter is intentionally self-contained and is NOT wired into Bukkit's
 * command dispatching globally - it only guards the specific call sites that
 * accept externally supplied command strings. Commands executed internally
 * by Skript, other plugins, Bukkit, or the server itself are unaffected.
 * <p>
 * Security model: when {@code command-filter.enabled} is {@code true}, every
 * command is blocked unless its command token exactly (case-insensitively)
 * matches an entry in {@code command-filter.allowed-commands}. Missing or
 * empty configuration fails closed (nothing is allowed) rather than open.
 */
public class CommandFilter {

    private CommandFilter() {
        // Utility class - no instances
    }

    /**
     * Determines whether a command submitted through an external ServerTap
     * command execution route is permitted to run.
     *
     * @param main    the ServerTapMain plugin instance, used to read config
     * @param command the raw, externally supplied command string
     * @return true if the command may be dispatched, false if it must be blocked
     */
    public static boolean isAllowed(ServerTapMain main, String command) {
        FileConfiguration config = main.getConfig();

        // Preserve original ServerTap behavior when the filter is off.
        if (!config.getBoolean("command-filter.enabled", false)) {
            return true;
        }

        String commandToken = extractCommandToken(command);

        // Null/empty/unparseable input can never be allowed while the filter is on.
        if (commandToken == null) {
            return false;
        }

        List<String> allowedCommands = config.getStringList("command-filter.allowed-commands");

        // Fail closed: no allowlist (or an empty one) means nothing is allowed.
        if (allowedCommands == null || allowedCommands.isEmpty()) {
            return false;
        }

        for (String allowed : allowedCommands) {
            if (allowed == null) {
                continue;
            }

            String normalizedAllowed = normalizeToken(allowed);
            if (!normalizedAllowed.isEmpty() && normalizedAllowed.equalsIgnoreCase(commandToken)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Safely normalizes a raw command string and extracts just the command
     * token (the first whitespace-delimited word), with any leading slash(es)
     * stripped. Namespaces such as {@code minecraft:} or {@code bukkit:} are
     * preserved as part of the token, since they are treated as distinct
     * commands that must be explicitly allowlisted.
     *
     * @param command the raw command string
     * @return the normalized, lowercase command token, or null if the
     * command is null or resolves to an empty string
     */
    private static String extractCommandToken(String command) {
        if (command == null) {
            return null;
        }

        String trimmed = command.trim();

        // Strip one or more leading "/" characters (e.g. "//command" -> "command").
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }

        trimmed = trimmed.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        // Extract only the first token (the command name), ignoring arguments.
        String firstToken = trimmed.split("\\s+", 2)[0];

        if (firstToken.isEmpty()) {
            return null;
        }

        return firstToken.toLowerCase();
    }

    /**
     * Normalizes an allowlist entry the same way an incoming command token is
     * normalized (trim + strip leading slashes + lowercase), so config
     * entries like {@code /mawar} still match an incoming {@code mawar}.
     */
    private static String normalizeToken(String entry) {
        String token = extractCommandToken(entry);
        return token == null ? "" : token;
    }
}
