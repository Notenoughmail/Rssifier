package com.notenoughmail.rssifier.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.notenoughmail.rssifier.Rssifier;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.notenoughmail.rssifier.RssifierFormatting.*;

public sealed interface Description permits Description.Absent, Description.Simple, Description.Complex {

    Element makeDescription(Document site, FeedDef def, String postTitle, Rssifier rssifier);

    static Element base() {
        return new Element("description");
    }

    static Description parse(JsonObject json, @Nullable String baseQuery, String title, Rssifier rss) {
        return switch (json.get("description")) {
            case null -> Absent.INSTANCE;
            case JsonPrimitive prim -> new Simple(PostDef.appendBaseQuery(baseQuery, prim));
            case JsonArray array -> {
                final String[] queries = new String[array.size()];
                for (int i = 0 ; i < queries.length ; i++) {
                    queries[i] = PostDef.appendBaseQuery(baseQuery, array.get(i));
                }
                yield new Complex(queries);
            }
            case JsonElement desc -> {
                rss.err("Description definition for %s was an unexpected value, should be absent, a string, or an array of strings, was %s (%s)".formatted(i(title), json(desc), b(desc.getClass().getSimpleName())));
                yield Absent.INSTANCE;
            }
        };
    }

    enum Absent implements Description {
        INSTANCE;

        @Override
        public Element makeDescription(Document site, FeedDef def, String postTitle, Rssifier rss) {
            return base().appendText(postTitle);
        }
    }

    record Simple(String query) implements Description {

        @Override
        public Element makeDescription(Document site, FeedDef def, String postTitle, Rssifier rss) {
            final Element desc = base();
            final String descText = Optional.ofNullable(site.selectFirst(query))
                    .map(elm -> elm.wholeText().trim())
                    .orElseGet(() -> {
                            rss.couldNotFind(query, "post description", "post title", def, site);
                            return postTitle;
                    });
            desc.appendText(descText);
            return desc;
        }
    }

    record Complex(String[] queries) implements Description {

        @Override
        public Element makeDescription(Document site, FeedDef def, String postTitle, Rssifier rss) {
            final Element desc = base();
            for (String query : queries) {
                final Element elm = site.selectFirst(query);
                if (elm == null) {
                    rss.queryFailed("Could not find description component with query %s in site %s (%s), skipping".formatted(b(query), url(def.url()), i(def.title())), site, query);
                    continue;
                }
                desc.appendText(elm.outerHtml());
            }
            return desc;
        }
    }
}
