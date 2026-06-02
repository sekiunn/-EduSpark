package com.eduspark.eduspark.util;

import org.springframework.web.util.HtmlUtils;

public final class LessonPlanContentUtils {

    private LessonPlanContentUtils() {
    }

    public static String normalizeMarkdownContent(String content) {
        if (!hasText(content)) {
            return "";
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        if (looksLikeHtml(normalized)) {
            normalized = htmlToMarkdown(normalized);
        }

        return normalized
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\f\\x0B]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static String toPlainText(String content) {
        String markdown = normalizeMarkdownContent(content);
        if (!hasText(markdown)) {
            return "";
        }

        return markdown
                .replaceAll("(?m)^```[a-zA-Z0-9_-]*\\s*$", "")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^>\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean looksLikeHtml(String value) {
        return value != null && value.matches("(?is).*</?[a-z][^>]*>.*");
    }

    private static String htmlToMarkdown(String content) {
        String normalized = content
                .replaceAll("(?is)<h1[^>]*>(.*?)</h1>", "# $1\n\n")
                .replaceAll("(?is)<h2[^>]*>(.*?)</h2>", "## $1\n\n")
                .replaceAll("(?is)<h3[^>]*>(.*?)</h3>", "### $1\n\n")
                .replaceAll("(?is)<h4[^>]*>(.*?)</h4>", "#### $1\n\n")
                .replaceAll("(?is)<h5[^>]*>(.*?)</h5>", "##### $1\n\n")
                .replaceAll("(?is)<h6[^>]*>(.*?)</h6>", "###### $1\n\n")
                .replaceAll("(?is)<strong[^>]*>(.*?)</strong>", "**$1**")
                .replaceAll("(?is)<b[^>]*>(.*?)</b>", "**$1**")
                .replaceAll("(?is)<em[^>]*>(.*?)</em>", "*$1*")
                .replaceAll("(?is)<i[^>]*>(.*?)</i>", "*$1*")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li[^>]*>", "- ")
                .replaceAll("(?is)</li>", "\n")
                .replaceAll("(?is)</(p|div|blockquote)>", "\n\n")
                .replaceAll("(?is)</(ul|ol|table|tbody|thead|tr)>", "\n")
                .replaceAll("(?is)<[^>]+>", "");

        return HtmlUtils.htmlUnescape(normalized)
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
