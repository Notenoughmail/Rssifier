package com.notenoughmail.rssifier.components;

import com.google.gson.JsonObject;
import com.notenoughmail.rssifier.Rssifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static com.notenoughmail.rssifier.RssifierFormatting.json;

public record Config(
        int keep,
        int statusKeep,
        boolean debug,
        List<FeedDef> feeds
) {
    public static Config parse(JsonObject json, Path feedsPath, Rssifier rss) {
        final int keep; // has to be final in order to be passed into the lambda
        int statusKeep = 5;
        if (json.has("feed_post_keep")) {
            keep = json.get("feed_post_keep").getAsInt();
        } else {
            keep = 10;
        }
        if (json.has("status_post_keep")) {
            statusKeep = json.get("status_post_keep").getAsInt();
        }
        final boolean debug = json.has("debug") && json.get("debug").getAsBoolean();
        final List<FeedDef> feeds = json.get("feeds").getAsJsonArray().asList().stream().map(elm -> {
            if (elm.isJsonObject()) {
                return FeedDef.parse(elm.getAsJsonObject(), keep, feedsPath, rss);
            }
            rss.err("Error parsing feed definition %smust be a json object".formatted(json(elm)));
            return null;
        }).filter(Objects::nonNull).toList();
        return new Config(keep, statusKeep, debug, feeds);
    }

    public static Config onError() {
        return new Config(10, 5, true, List.of());
    }
}
