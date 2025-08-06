package com.notenoughmail.rssifier.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.notenoughmail.rssifier.Rssifier;
import org.jspecify.annotations.Nullable;

import static com.notenoughmail.rssifier.RssifierFormatting.*;

public record PostDef(
        String title,
        @Nullable String publishDate,
        String permalink,
        Description description,
        @Nullable String author,
        boolean multiAuthor
) {
    @Nullable
    static PostDef parse(JsonObject json, String title, Rssifier rss) {
        final String baseQuery = json.has("base_query") ? json.get("base_query").getAsString() : null;
        if (json.has("permalink") && json.has("title")) {
            return new PostDef(
                    appendBaseQuery(baseQuery, json.get("title")),
                    json.has("publish_date") ? appendBaseQuery(baseQuery, json.get("publish_date")) : null,
                    appendBaseQuery(baseQuery, json.get("permalink")),
                    Description.parse(json, baseQuery, title, rss),
                    json.has("author") ? appendBaseQuery(baseQuery, json.get("author")) : null,
                    json.has("multi_author") && json.get("multi_author").getAsBoolean()
            );
        } else {
            rss.err(
                    "Post definition requires %s properties, %s post definition looks like %sand is missing".formatted(
                            boldArray("permalink", "title"),
                            i(title),
                            json(json)
                    ),
                    FeedDef.missing(json.keySet(), "title", "permalink")
            );
            return null;
        }
    }

    public static String appendBaseQuery(@Nullable String base, JsonElement query) {
        return base == null ? query.getAsString() : base + " " + query.getAsString();
    }
}
