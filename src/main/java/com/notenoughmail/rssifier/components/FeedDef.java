package com.notenoughmail.rssifier.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.notenoughmail.rssifier.Rssifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.notenoughmail.rssifier.RssifierFormatting.*;

public record FeedDef(
        String url,
        String title,
        Path file,
        int keep,
        boolean verifyUniqueness,
        boolean guid,
        @Nullable Duration timeBetweenQueries,
        EnumSet<DayOfWeek> daysOfWeek,
        PostDef posts
) {
    @Nullable
    static FeedDef parse(JsonObject json, int defaultKeep, Path feedsPath, Rssifier rss) {
        if (
                json.has("url") &&
                json.has("file") &&
                json.has("description") &&
                json.has("title") &&
                json.has("post")
        ) {
            final Path feedLocation = feedsPath.resolve("%s.xml".formatted(json.get("file").getAsString()));
            final String title = json.get("title").getAsString();
            if (!feedLocation.toFile().exists()) {
                try {
                    rss.initFeed(
                            json.get("file").getAsString(),
                            title,
                            json.get("description").getAsString(),
                            json.get("url").getAsString()
                    );
                } catch (IOException exception) {
                    rss.err("Error creating feed file for %s".formatted(i(title)), exception);
                    return null;
                }
            }
            if (json.has("post") && json.get("post").isJsonObject()) {
                final PostDef posts = PostDef.parse(json.getAsJsonObject("post"), title, rss);
                if (posts != null) {
                    return new FeedDef(
                            json.get("url").getAsString(),
                            title,
                            feedLocation,
                            json.has("keep_posts") ? json.get("keep_posts").getAsInt() : defaultKeep,
                            json.has("verify_uniqueness") && json.get("verify_uniqueness").getAsBoolean(),
                            !json.has("guid") || json.get("guid").getAsBoolean(),
                            json.has("time_between_queries") ? parseDuration(json.get("time_between_queries"), title, rss) : null,
                            json.has("days_of_week") ? parseDaysOfWeek(json.get("days_of_week"), title, rss) : EnumSet.allOf(DayOfWeek.class),
                            posts
                    );
                }
            } else {
                rss.err("Error parsing %s post definition %smust be a json object".formatted(
                        i(title),
                        json(json.get("post"))
                ));
            }
        } else {
            missing(rss, "Feed definition", json, "url", "file", "description", "title", "post");
        }
        return null;
    }

    @Nullable
    static Duration parseDuration(JsonElement json, String title, Rssifier rss) {
        try {
            if (json instanceof JsonObject obj) {
                String out = "P";
                if (obj.has("days")) {
                    out += (Math.abs(obj.get("days").getAsInt()) + "D");
                }
                final boolean hours = obj.has("hours"), minutes = obj.has("minutes");
                if (hours || minutes) {
                    out += "T";
                }
                if (hours) {
                    out += (Math.abs(obj.get("hours").getAsInt()) + "H");
                }
                if (minutes) {
                    out += (Math.abs(obj.get("minutes").getAsInt()) + "M");
                }
                if (out.length() > 1) {
                    return Duration.parse(out);
                } else {
                    rss.err("Unable to parse %sinto a valid duration for %s, must have at least one of: <b>days</b>, <b>hours</b>, or <b>minutes</b>".formatted(json(json), i(title)));
                }
            } else {
                rss.err("Durations may only be objects, was", json(json));
            }
        } catch (Exception e) {
            rss.err("Failed to parse duration", e);
        }
        return null;
    }

    static EnumSet<DayOfWeek> parseDaysOfWeek(JsonElement json, String title, Rssifier rss) {
        if (json instanceof JsonArray array) {
            final EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            array.forEach(elm -> {
                if (elm instanceof JsonPrimitive prim && prim.isString()) {
                    try {
                        final DayOfWeek day = DayOfWeek.valueOf(prim.getAsString().toUpperCase(Locale.ROOT));
                        days.add(day);
                    } catch (Exception e) {
                        rss.err("Unknown day of week", json(elm));
                    }
                } else {
                    rss.err("Days of week in %s feed must be a string value".formatted(i(title)), json(elm));
                }
            });
            if (days.isEmpty()) {
                rss.err("No valid days found for %s feed".formatted(i(title)));
            }
            return days;
        } else {
            rss.err("Days of week in %s was not an array".formatted(i(title)), json(json));
        }
        return EnumSet.allOf(DayOfWeek.class);
    }

    public static String missing(Set<String> has, String... required) {
        final Set<String> missing = new HashSet<>(Set.of(required));
        missing.removeIf(has::contains);
        return boldArray(missing.toArray(String[]::new));
    }

    public static void missing(Rssifier rss, String what, JsonObject json, String... required) {
        rss.err(
                "%s requires %s properties, definition looks like %sand is missing".formatted(
                        what,
                        boldArray(required),
                        json(json)
                ),
                missing(
                        json.keySet(),
                        required
                )
        );
    }
}
