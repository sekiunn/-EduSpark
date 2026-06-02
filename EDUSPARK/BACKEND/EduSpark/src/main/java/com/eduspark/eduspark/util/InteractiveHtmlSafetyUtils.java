package com.eduspark.eduspark.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML safety helpers for interactive workspaces.
 */
public final class InteractiveHtmlSafetyUtils {

    private static final Pattern SCRIPT_BLOCK_PATTERN =
            Pattern.compile("(?is)<script\\b[^>]*>[\\s\\S]*?</script>");
    private static final Pattern EVENT_HANDLER_ATTR_PATTERN =
            Pattern.compile("(?is)\\s+on[a-z0-9_-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JAVASCRIPT_URL_ATTR_PATTERN =
            Pattern.compile("(?is)\\s+(href|src|xlink:href|action)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*'|javascript:[^\\s>]+)");
    private static final Pattern FORBIDDEN_CONTAINER_PATTERN =
            Pattern.compile("(?is)<(?:iframe|object|embed|applet|base)\\b[^>]*>[\\s\\S]*?</(?:iframe|object|embed|applet|base)>|<(?:iframe|object|embed|applet|base)\\b[^>]*/?>");
    private static final Pattern META_REFRESH_PATTERN =
            Pattern.compile("(?is)<meta\\b[^>]*http-equiv\\s*=\\s*(?:\"refresh\"|'refresh'|refresh)[^>]*>");

    private InteractiveHtmlSafetyUtils() {
    }

    public static SanitizationResult sanitizeGeneratedHtml(String html) {
        return new SanitizationResult(stripDangerousContainers(normalizeHtml(html)), false);
    }

    public static SanitizationResult sanitizeManualHtml(String incomingHtml, String existingHtml) {
        String normalizedIncoming = stripDangerousContainers(normalizeHtml(incomingHtml));
        boolean preserveExecutableSurface = hasSameExecutableSurface(existingHtml, normalizedIncoming);
        if (preserveExecutableSurface) {
            return new SanitizationResult(normalizedIncoming, false);
        }
        String sanitized = stripExecutableContent(normalizedIncoming);
        return new SanitizationResult(sanitized, !normalizedIncoming.equals(sanitized));
    }

    public static String normalizeHtml(String html) {
        return html == null ? "" : html.trim();
    }

    private static String stripDangerousContainers(String html) {
        String sanitized = html == null ? "" : html;
        sanitized = FORBIDDEN_CONTAINER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = META_REFRESH_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized.trim();
    }

    private static String stripExecutableContent(String html) {
        String sanitized = SCRIPT_BLOCK_PATTERN.matcher(html).replaceAll("");
        sanitized = EVENT_HANDLER_ATTR_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_URL_ATTR_PATTERN.matcher(sanitized).replaceAll(" $1=\"#\"");
        return sanitized.trim();
    }

    private static boolean hasSameExecutableSurface(String existingHtml, String incomingHtml) {
        return extractExecutableSurface(existingHtml).equals(extractExecutableSurface(incomingHtml));
    }

    private static List<String> extractExecutableSurface(String html) {
        String normalized = normalizeHtml(html);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> surface = new ArrayList<>();
        collectMatches(surface, SCRIPT_BLOCK_PATTERN, normalized);
        collectMatches(surface, EVENT_HANDLER_ATTR_PATTERN, normalized);
        collectMatches(surface, JAVASCRIPT_URL_ATTR_PATTERN, normalized);
        return surface;
    }

    private static void collectMatches(List<String> target, Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            target.add(normalizeExecutableToken(matcher.group()));
        }
    }

    private static String normalizeExecutableToken(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public record SanitizationResult(String html, boolean executableContentRemoved) {
    }
}
