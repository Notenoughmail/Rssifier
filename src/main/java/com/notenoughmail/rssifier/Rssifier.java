package com.notenoughmail.rssifier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.ParseSettings;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class Rssifier {

    private static final Gson GSON = new Gson();
    private static final StringBuilder errors = new StringBuilder();

    private static final FileSystem fs = FileSystems.getDefault();
    private static final String executionPath = System.getProperty("user.dir");
    @Nullable
    private static Path feedsPath, statusPath;

    public static void main(String[] args) {
        if (setup()) {
            handleFeeds();
            handleAnyErrors();
        }
    }

    private static boolean setup() {
        try {
            feedsPath = Files.createDirectories(fs.getPath(executionPath, "feeds"));
            statusPath = feedsPath.resolve("Rssifier Status.xml");
            if (!statusPath.toFile().exists()) {
                initFeed(
                        "Rssifier Status",
                        "Rssifier Status",
                        "An RSS feed for any errors Rssifier encounters",
                        null
                );
            }
        } catch (Exception exception) {
            err(exception);
            err("Errors in Rssifier initialization! Stopping now");
            System.out.println(errors);
            return false;
        }
        return true;
    }

    private static void handleFeeds() {
        assert feedsPath != null;
        final File feeds = fs.getPath(executionPath, "feeds.json").toFile();
        if (feeds.exists()) {
            try {
                final List<FeedDef> feedDefs = GSON.fromJson(
                        new FileReader(feeds),
                        JsonArray.class
                ).asList().stream().map(Rssifier::parseFeedConfig).filter(Objects::nonNull).toList();

                for (FeedDef def : feedDefs) {
                    final Document site = Jsoup.connect(def.url()).get();
                    final PostDef siteInfo = def.posts();
                    final Element title = site.selectFirst(siteInfo.title());
                    if (title == null) {
                        err("Could not select title with query %s in site %s".formatted(siteInfo.title(), def.url()));
                        break;
                    }

                    final String postTitle = title.wholeOwnText();
                    final Path feedLocation = feedsPath.resolve(def.file());
                    final Document feed = Jsoup.parse(feedLocation, null, "", Parser.xmlParser().settings(ParseSettings.preserveCase));
                    final Element channel = feed.getElementsByTag("channel").getFirst();
                    final Elements items = channel.getElementsByTag("item");

                    boolean postAlreadyExists = false;
                    for (Element item : items) {
                        final Element itemTitle = item.getElementsByTag("title").getFirst();
                        if (itemTitle != null) {
                            if (itemTitle.text().equals(postTitle)) {
                                postAlreadyExists = true;
                                break;
                            }
                        } else {
                            err("Existing post did not have a 'title' element? Skipping processing");
                            break;
                        }
                    }

                    if (!postAlreadyExists) {
                        final String
                                postDate = siteInfo.publishDate() == null ? nowDateString() : mapNullable(site.selectFirst(siteInfo.publishDate()), elm -> {
                                    if (elm == null) {
                                        err("Could not find publish date with query %s, using current time".formatted(siteInfo.publishDate()));
                                        return nowDateString();
                                    }
                                    return elm.text();
                                }),
                                postLink = mapNullable(site.selectFirst(siteInfo.permalink()), elm -> {
                                    if (elm == null) {
                                        err("Could not find permalink with query %s, using site url".formatted(siteInfo.permalink()));
                                        return def.url();
                                    }
                                    return elm.attr("abs:href");
                                }),
                                postDesc = siteInfo.description() == null ? postTitle : mapNullable(site.selectFirst(siteInfo.description()), elm -> {
                                    if (elm == null) {
                                        err("Could not find post description with query %s, using post title".formatted(siteInfo.description()));
                                        return postTitle;
                                    }
                                    return elm.text();
                                }),
                                postAuth = siteInfo.author() == null ? null : mapNullable(site.selectFirst(siteInfo.author()), elm -> {
                                    if (elm == null) {
                                        err("Could not find post author with query %s".formatted(siteInfo.author()));
                                        return "";
                                    }
                                    return elm.text();
                                });

                        final Element post = new Element("item", Parser.NamespaceXml);
                        post.insertChildren(-1,
                                new Element("title").appendText(postTitle),
                                new Element("pubDate").appendText(postDate),
                                new Element("description").appendText(postDesc),
                                new Element("link").appendText("%s?utm_source=rss".formatted(postLink)),
                                new Element("guid").appendText(postLink)
                        );
                        assert postAuth != null; // I don't know why IDEA thinks this might be null
                        if (!postAuth.isEmpty()) {
                            post.insertChildren(-1, new Element("author").appendText(postAuth));
                        }
                        items.addFirst(post);
                        while (items.size() > def.keep()) {
                            items.removeLast();
                        }

                        final Element processedChannel = new Element("channel", Parser.NamespaceXml);
                        copyToNewChannel(processedChannel, channel, items);

                        feed.getElementsByTag("channel").set(0, processedChannel);
                        final FileWriter fileWriter = new FileWriter(feedLocation.toFile());
                        final PrintWriter print = new PrintWriter(fileWriter);
                        print.println(feed);
                        print.close();
                    }
                }
            } catch (IOException exception) {
                err("Error creating/updating feeds", exception);
            }
        } else {
            err("feeds.json file does not exist!");
        }
    }

    private static void handleAnyErrors() {
        assert statusPath != null;
        if (!errors.isEmpty()) {
            try {
                final Document status = Jsoup.parse(statusPath, null, "", Parser.xmlParser().settings(ParseSettings.preserveCase));
                final Element channel = status.getElementsByTag("channel").getFirst();
                final Elements items = channel.getElementsByTag("item");
                items.addFirst(errorPost());
                while (items.size() > 10) {
                    items.removeLast();
                }
                final Element processedChannel = new Element("channel", Parser.NamespaceXml);
                copyToNewChannel(processedChannel, channel, items);
                status.getElementsByTag("channel").set(0, processedChannel);

                final FileWriter fileWriter = new FileWriter(statusPath.toFile());
                final PrintWriter print = new PrintWriter(fileWriter);
                print.println(status);
                print.close();
            } catch (Exception e) {
                err("Unable to create error post!");
                err(e);
                System.out.println(errors);
            }
        }
    }

    private static Element errorPost() {
        final Element post = new Element("item", Parser.NamespaceXml);
        post.insertChildren(
                -1,
                new Element("title").appendText("Errors while running Rssifier"),
                new Element("pubDate").appendText(nowDateString()),
                new Element("author").appendText("Rssifier"),
                new Element("description").appendText(errors.toString().replace("\n", "<p>"))
        );
        return post;
    }

    private static void copyToNewChannel(Element newChannel, Element oldChannel, Elements items) {
        newChannel.insertChildren(-1, oldChannel.getElementsByTag("generator"));
        newChannel.insertChildren(-1, oldChannel.getElementsByTag("title"));
        newChannel.insertChildren(-1, oldChannel.getElementsByTag("description"));
        newChannel.insertChildren(-1, oldChannel.getElementsByTag("image"));
        newChannel.insertChildren(-1, items);
    }

    private static String nowDateString() {
        return LocalDate.now() + " " + LocalTime.now();
    }

    @Nullable
    private static FeedDef parseFeedConfig(JsonElement json) {
        assert feedsPath != null;
        if (json.isJsonObject()) {
            final JsonObject obj = json.getAsJsonObject();
            if (
                    obj.has("url") &&
                    obj.has("file") &&
                    obj.has("description") &&
                    obj.has("title") &&
                    obj.has("post")
            ) {
                final Path feedLocation = feedsPath.resolve("%s.xml".formatted(obj.get("file").getAsString()));
                if (!feedLocation.toFile().exists()) {
                    try {
                        initFeed(
                                obj.get("file").getAsString(),
                                obj.get("title").getAsString(),
                                obj.get("description").getAsString(),
                                obj.get("url").getAsString()
                        );
                    } catch (IOException exception) {
                        err("Error creating feed file", exception);
                        return null;
                    }
                }
                final PostDef posts;
                if (obj.get("post").isJsonObject()) {
                    final JsonObject post = obj.getAsJsonObject("post");
                    if (post.has("permalink") && post.has("title")) {
                        posts = new PostDef(
                                post.get("title").getAsString(),
                                post.has("publishDate") ? post.get("publishDate").getAsString() : null,
                                post.get("permalink").getAsString(),
                                post.has("description") ? post.get("description").getAsString() : null,
                                post.has("author") ? post.get("author").getAsString() : null
                        );
                    } else {
                        err("Post definition requires 'permalink' and 'title' properties");
                        return null;
                    }
                } else {
                    err("Error parsing feed definition %S".formatted(json), "Post definition must be a json object");
                    return null;
                }
                return new FeedDef(obj.get("url").getAsString(), feedLocation, obj.has("keepPosts") ? obj.get("keepPosts").getAsInt() : 10, posts);
            } else {
                err("Feed definition requires 'url', 'file', 'description', 'title', and 'post' properties");
                return null;
            }
        } else {
            err("Error parsing feed definition %s".formatted(json), "Feed definition must be a json object");
            return null;
        }
    }

    private static void err(Object err) {
        err(null, err);
    }

    private static void err(@Nullable String prefix, Object err) {
        if (!errors.isEmpty()) {
            errors.append('\n');
        }
        if (prefix != null) {
            errors.append(prefix);
            errors.append(':');
            errors.append(' ');
        }
        if (err instanceof Throwable thr) {
            errors.append(thr.getMessage());
            for (StackTraceElement stack : thr.getStackTrace()) {
                errors.append("\n\tat ");
                errors.append(stack);
            }
        } else {
            errors.append(err);
        }
    }

    private static void initFeed(String name, String title, String description, @Nullable String link) throws IOException {
        assert feedsPath != null;
        final Path feedPath = feedsPath.resolve("%s.xml".formatted(name));
        final String linkText = link == null ? feedPath.toUri().toString() : link;
        final StringBuilder feedBuilder = new StringBuilder();

        feedBuilder.append(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
                    <channel>
                        <generator>NotEnoughMail's Rssifier Project</generator>
                        <title>%s</title>
                        <description>%s</description>
                        <link>%s</link>
                """.formatted(title, description, linkText)
        );

        final Path iconPath = feedsPath.resolve("%s.ico".formatted(name));
        if (iconPath.toFile().exists()) {
            feedBuilder.append(
                    """
                            <image>
                                <title>%s</title>
                                <link>%s</link>
                                <url>%s</url>
                            </image>
                    """.formatted(title, linkText, iconPath.toAbsolutePath().toUri())
            );
        }

        feedBuilder.append(
                """
                    </channel>
                </rss>
                """
        );

        final FileWriter writer = new FileWriter(feedPath.toFile());
        final PrintWriter print = new PrintWriter(writer);
        print.println(feedBuilder);
        print.close();
    }

    private static <R, T> R mapNullable(@Nullable T element, Function<@Nullable T, R> extractor) {
        return extractor.apply(element);
    }

    private record FeedDef(
            String url,
            Path file,
            int keep,
            PostDef posts
    ) {}

    private record PostDef(
            String title,
            @Nullable String publishDate,
            String permalink,
            @Nullable String description
            ,
            @Nullable String author
    ) {}
}
