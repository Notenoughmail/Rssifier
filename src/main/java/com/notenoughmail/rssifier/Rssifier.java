package com.notenoughmail.rssifier;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.notenoughmail.rssifier.components.Config;
import com.notenoughmail.rssifier.components.FeedDef;
import com.notenoughmail.rssifier.components.PostDef;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.ParseSettings;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import static com.notenoughmail.rssifier.RssifierFormatting.*;

public class Rssifier {

    private final StringBuilder errors = new StringBuilder();
    private final boolean setup;
    // These fields should be treated as final, despite not being marked as such
    // They are not final-ed due to the misery that is working with final fields and try-catch blocks
    private Path feedsPath, statusPath;
    private final Config config;

    private Rssifier() {
        final String userDir = System.getProperty("user.dir");
        try {
            feedsPath = Files.createDirectories(Path.of(userDir, "feeds"));
            statusPath = feedsPath.resolve("Rssifier Status.xml");
            if (!statusPath.toFile().exists()) {
                initFeed(
                        "Rssifier Status",
                        "Rssifier Status",
                        "An RSS feed for any errors Rssifier encounters",
                        null
                );
            }
        } catch (Exception e) {
            err(e);
            err("Errors in Rssifier initialization! Stopping now");
            System.out.println(errors);
            feedsPath = statusPath = Path.of("_");
            setup = false;
            config = Config.onError();
            return;
        }
        final File configPath = Path.of(userDir, "config.json").toFile();
        if (configPath.exists()) {
            boolean valid;
            Config conf;
            try {
                Gson gson = new Gson();
                conf = Config.parse(
                        gson.fromJson(
                                new FileReader(configPath),
                                JsonObject.class
                        ),
                        feedsPath,
                        this
                );
                valid = true;
            } catch (Exception e) {
                err("Could not read feed config file", e);
                conf = Config.onError();
                valid = false;
            }
            config = conf;
            setup = valid;
        } else {
            err("<strong>config.json</strong> file does not exist!");
            setup = false;
            config = Config.onError();
        }
    }

    public static void main(String[] args) {
        final Rssifier instance = new Rssifier();
        if (instance.setup) {
            instance.handleFeeds();
        }
        instance.handleAnyErrors();
    }

    private void handleFeeds() {
        final LocalDateTime now = LocalDateTime.now();
        final DayOfWeek today = now.getDayOfWeek();
        for (FeedDef def : config.feeds()) {
            try {
                final Path feedLocation = feedsPath.resolve(def.file());
                final Document feed = Jsoup.parse(feedLocation, null, "", Parser.xmlParser().settings(ParseSettings.preserveCase));

                if (def.daysOfWeek().contains(today) && (def.timeBetweenQueries() == null || durationHasElapsed(def.timeBetweenQueries(), feed, now))) {
                    // Before timeStamp replacement so that the time stamp is not updated if there is an issue opening the site
                    final Document site = Jsoup.connect(def.url()).get();

                    if (def.timeBetweenQueries() != null) {
                        feed.select("timeStamp").remove();
                        feed.getElementsByTag("rss").getFirst().appendChild(new Element("timeStamp").appendText(nowDateString()));
                    }

                    final PostDef siteInfo = def.posts();
                    final Element title = site.selectFirst(siteInfo.title());
                    if (title == null) {
                        couldNotFind(siteInfo.title(), "title", null, def, site);
                        continue;
                    }

                    final String postTitle = title.wholeText().replace('\n', ' ').trim();
                    final String postLink = postLink(def, siteInfo, site);
                    final Element channel = feed.getElementsByTag("channel").getFirst();
                    final Elements items = channel.getElementsByTag("item");

                    if (isPostNew(items, postTitle, postLink, def)) {
                        final String postAuth = postAuth(def, siteInfo, site);

                        final Element post = new Element("item", Parser.NamespaceXml);
                        post.insertChildren(-1,
                                new Element("title").appendText(postTitle),
                                new Element("pubDate").appendText(postDate(def ,siteInfo, site)),
                                siteInfo.description().makeDescription(site, def, postTitle, this),
                                new Element("link").appendText("%s?utm_source=rss".formatted(postLink))
                        );

                        if (def.guid())
                            post.insertChildren(-1, new Element("guid").appendText(postLink));
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
                        print.print(feed.outerHtml());
                        print.close();
                    }
                }
            } catch (Exception exception) {
                err("Error creating/updating %s feed".formatted(i(def.title())), exception);
            }
        }
    }

    private String postLink(FeedDef def, PostDef siteInfo, Document site) {
        final Element elm = site.selectFirst(siteInfo.permalink());
        if (elm == null) {
            couldNotFind(siteInfo.permalink(), "permalink", "site url", def, site);
            return def.url();
        }
        final String link = elm.attr("abs:href").trim();
        if (link.isEmpty()) {
            err("Found permalink element for site %s, but href attribute was absent or blank".formatted(i(def.title())));
            return def.url();
        }
        return link;
    }

    private String postDate(FeedDef def, PostDef siteInfo, Document site) {
        if (siteInfo.publishDate() == null) return nowDateString();
        final Element elm = site.selectFirst(siteInfo.publishDate());
        if (elm == null) {
            couldNotFind(siteInfo.publishDate(), "publish date", "current time", def, site);
            return nowDateString();
        }
        return elm.text().trim();
    }

    private String postAuth(FeedDef def, PostDef siteInfo, Document site) {
        if (siteInfo.author() == null) return "";
        if (siteInfo.multiAuthor()) {
            final String authors = site.selectStream(siteInfo.author())
                    .map(elm -> elm.text().trim())
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" & "));
            if (authors.isEmpty()) {
                couldNotFind(siteInfo.author(), "post authors", null, def, site);
                return "";
            }
            return authors;
        } else {
            final Element elm = site.selectFirst(siteInfo.author());
            if (elm == null) {
                couldNotFind(siteInfo.author(), "post author", null, def, site);
                return "";
            }
            return elm.text().trim();
        }
    }

    private boolean isPostNew(Elements items, String title, String link, FeedDef def) {
        for (Element item : items) {
            final Elements titleElements = item.getElementsByTag("title");
            if (!titleElements.isEmpty()) {
                if (titleElements.getFirst().text().trim().equals(title)) {
                    if (def.verifyUniqueness()) {
                        final Elements linkElements = item.getElementsByTag("link");
                        if (!linkElements.isEmpty() && linkElements.getFirst().text().trim().equals(link)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            } else {
                err("Existing %s post %s did not have a 'title' element?".formatted(
                        i(def.title()),
                        html(item)
                ));
            }
        }
        return true;
    }

    public void couldNotFind(String query, String what, @Nullable String using, FeedDef def, Document site) {
        using = (using == null ? "" : " using " + using);
        queryFailed("Could not find %s with query %s in site %s (%s)".formatted(what, b(query), url(def.url()), i(def.title())) + using, site, query);
    }

    public void queryFailed(String msg, Document doc, String query) {
        if (!config.debug()) {
            err(msg);
            return;
        }

        query = query.replaceAll(" +", " ").replaceAll("\\(> ", "(>*");

        final StringBuilder msgBuilder = new StringBuilder(msg);
        msgBuilder.append("\n\nClipping query until an element is found:\n");

        int index = query.lastIndexOf(' ');
        while (index > 0) {
            query = query.substring(0, index).trim();
            if (query.charAt(index - 1) == '>') {
                query = query.substring(0, index - 1).trim();
            }

            try {
                final Element search = doc.selectFirst(query.replaceAll("\\(>\\*", "(> "));
                if (search == null) {
                    msgBuilder.append("Nothing found with query %s\n".formatted(b(query.replaceAll("\\(>\\*", "(> "))));
                } else {
                    msgBuilder.append("<span class=\"rssifier-p\">Found %s element with query %s</span>\n\n".formatted(describeElement(search), b(query.replaceAll("\\(>\\*", "(> "))));
                    msgBuilder.append("Direct children elements:\n<blockquote>");
                    final Elements children = search.children();
                    if (children.isEmpty()) {
                        msgBuilder.append("None!");
                    } else {
                        children.forEach(elm -> msgBuilder.append(describeElement(elm)).append(" fully qualified selector: <b>").append(elm.cssSelector()).append("</b>\n"));
                    }
                    msgBuilder.append("</blockquote>");
                    break;
                }
            } catch (Exception ignored) {
                // Space splits may cause issues with :contains(hello world) selectors, simply skip over 'internal' spaces
                // Maybe this should clip to last '(' to prevent multiple exceptions with multiple 'internal' spaces
            }

            index = query.lastIndexOf(' ');
        }

        err(msgBuilder.toString());
    }

    private static String describeElement(Element element) {
        String elmDesc = element.tagName();
        if (!element.id().isEmpty()) {
            elmDesc += "#" + element.id();
        }
        if (!element.className().isEmpty()) {
            elmDesc += "." + element.className().replace(' ', '.');
        }
        return u(i(elmDesc));
    }

    private void handleAnyErrors() {
        if (!errors.isEmpty()) {
            System.out.println("Errors encountered! Attempting to make error post...");
            try {
                final Document status = Jsoup.parse(statusPath, null, "", Parser.xmlParser().settings(ParseSettings.preserveCase));
                final Element channel = status.getElementsByTag("channel").getFirst();
                final Elements items = channel.getElementsByTag("item");
                items.addFirst(errorPost());
                while (items.size() > config.statusKeep()) {
                    items.removeLast();
                }
                final Element processedChannel = new Element("channel", Parser.NamespaceXml);
                copyToNewChannel(processedChannel, channel, items);
                status.getElementsByTag("channel").set(0, processedChannel);

                final FileWriter fileWriter = new FileWriter(statusPath.toFile());
                final PrintWriter print = new PrintWriter(fileWriter);
                print.println(status);
                print.close();
                System.out.println("Successfully made error post");
            } catch (Exception e) {
                err("Unable to create error post!", e);
                System.out.println(errors);
            }
        }
    }

    private Element errorPost() {
        final Element post = new Element("item", Parser.NamespaceXml);
        errors.append("<hr>");
        post.insertChildren(
                -1,
                new Element("title").appendText("Errors while running Rssifier"),
                new Element("pubDate").appendText(nowDateString()),
                new Element("author").appendText("Rssifier"),
                new Element("description").appendText(errors.toString().replace("\n", "<br>\n"))
        );
        return post;
    }

    private static void copyToNewChannel(Element newChannel, Element oldChannel, Elements items) {
        final Elements children = oldChannel.children();
        children.removeIf(elm -> elm.tagName().equals("item"));
        newChannel.insertChildren(-1, children);
        newChannel.insertChildren(-1, items);
    }

    private static String nowDateString() {
        return DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
    }

    public void err(Object err) {
        err(null, err);
    }

    public void err(@Nullable String prefix, Object err) {
        if (!errors.isEmpty()) {
            errors.append('\n');
        } else {
            errors.append("<style>.rssifier-p{color:purple;} .rssifier-r{color:red;} .rssifier-g{color:green;} .rssifier-b{color:blue;}</style>");
        }
        if (prefix != null) {
            errors.append(prefix);
            errors.append(": ");
        }
        if (err instanceof Throwable thr) {
            errors.append("Error encountered:\n");
            errors.append("<blockquote><samp class=\"rssifier-r\">\n");
            thr(thr);
            errors.append("</samp></blockquote>");
        } else {
            errors.append(err);
        }
        errors.append("\n<hr>");
    }

    public void thr(Throwable thr) {
        errors.append(thr.getClass().getCanonicalName());
        errors.append('\n');
        errors.append(thr.getMessage());
        errors.append("\n<div style=\"margin-left: 2em\">");
        for (StackTraceElement stack : thr.getStackTrace()) {
            errors.append("\n\tat ");
            errors.append(sanitizeForHtml("%s".formatted(stack)));
        }
        errors.append("\n</div>");
        if (thr.getCause() != null && thr.getCause() != thr) {
            errors.append("\nCaused by:\n");
            thr(thr.getCause());
        }
    }

    static boolean durationHasElapsed(Duration duration, Document feed, LocalDateTime now) {
        final Element timeStamp = feed.selectFirst("timeStamp");

        return timeStamp == null || LocalDateTime.from(
                DateTimeFormatter.ISO_DATE_TIME.parse(timeStamp.ownText())
        ).plus(duration).isBefore(now);
    }

    public void initFeed(String name, String title, String description, @Nullable String link) throws IOException {
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
                    <timeStamp>1970-01-01T00:00:00</timeStamp>
                </rss>
                """
        );

        final FileWriter writer = new FileWriter(feedPath.toFile());
        final PrintWriter print = new PrintWriter(writer);
        print.println(feedBuilder);
        print.close();
    }
}
