package com.notenoughmail.rssifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jsoup.nodes.Element;

import java.util.function.UnaryOperator;

public class RssifierFormatting {

    public static String html(Element element) {
        return "\n<blockquote>%s</blockquote>\n".formatted(
                sanitizeForHtml(element.outerHtml().replace("><", ">\n<"))
                        .replace("&lt;", "<span class=\"rssifier-b\">&lt;")
                        .replace("&gt;", "&gt;</span>")
        );
    }

    public static String sanitizeForHtml(String text) {
        return text
                .replace("&", "&amps;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String i(String text) {
        return "<i>%s</i>".formatted(text);
    }

    public static String b(String text) {
        return "<b>%s</b>".formatted(text);
    }

    public static String url(String url) {
        return "<a href=\"%s\">%s</a>".formatted(url, u(url));
    }

    public static String u(String text) {
        return "<u>%s</u>".formatted(text);
    }

    public static String json(JsonElement json) {
        final StringBuilder builder = new StringBuilder();
        formatJson(builder, json, 0, true);
        return "\n\n<div style=\"margin-left: 2em;\" class=\"rssifier-g\"><code>%s</code></div>\n".formatted(builder.toString());
    }

    public static void formatJson(StringBuilder builder, JsonElement json, int indent, boolean initialIndent) {
        if (initialIndent) {
            builder.append("<span style=\"margin-left: ").append(2 * indent).append("em;\">");
        }
        switch (json) {
            case JsonPrimitive prim -> {
                if (prim.isString()) {
                    builder.append('\"').append(prim.getAsString()).append('\"');
                } else {
                    builder.append(prim.getAsString());
                }
            }
            case JsonArray array -> {
                builder.append("[\n");
                for (int i = 0 ; i < array.size() ; i++) {
                    formatJson(builder, array.get(i), indent + 1, true);
                    if (i < array.size() - 1) {
                        builder.append(',');
                    }
                    builder.append('\n');
                }
                builder.append("<span style=\"margin-left: ").append(2 * indent).append("em;\">]</span>");
            }
            case JsonObject obj -> {
                builder.append("{\n");
                final String[] keys = obj.keySet().toArray(String[]::new);
                for (int i = 0 ; i < keys.length ; i++) {
                    builder.append("<span style=\"margin-left: ").append(2 * (indent + 1)).append("em;\">");
                    builder.append('\"').append(keys[i]).append("\": ");
                    formatJson(builder, obj.get(keys[i]), indent + 1, false);
                    if (i < keys.length - 1) {
                        builder.append(',');
                    }
                    builder.append("</span>");
                    builder.append('\n');
                }
                builder.append("<span style=\"margin-left: ").append(2 * indent).append("em;\">}</span>");
            }
            default -> builder.append("null");
        }
        if (initialIndent) {
            builder.append("</span>");
        }
    }

    public static String boldArray(String... strings) {
        return formatArray(RssifierFormatting::b, strings);
    }

    public static String formatArray(UnaryOperator<String> formatter, String... strings) {
        return switch (strings.length) {
            case 0 -> throw new IllegalArgumentException("Array must have at least one element in order to format it");
            case 1 -> formatter.apply(strings[0]);
            case 2 -> formatter.apply(strings[0]) + " and " + formatter.apply(strings[1]);
            default -> {
                String text = formatter.apply(strings[0]);
                for (int i = 1 ; i < strings.length ; i++) {
                    text += (i == strings.length - 1 ? ", and " : ", ") + formatter.apply(strings[i]);
                }
                yield text;
            }
        };
    }
}
