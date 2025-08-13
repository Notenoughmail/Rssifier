# Rssifier

A (relatively) simple java program that can dynamically create local rss feeds from most websites

In all honesty, you probably shouldn't use this

Uses [Jsoup](https://jsoup.org/) 1.21.1 to retrieve and parse sites/files and [Gson](https://github.com/google/gson) to parse the config file, they are included within distributions under their own licenses

Designed to be run by a script one or two times a day and/or on computer startup

Built and compiled with Java 21

## Usage

In the same directory as the jar is *invoked* in, have a `config.json` file. This is a json object with the following properties:

| Property           | Optional | Type      | Description                                                                             |
|--------------------|----------|-----------|-----------------------------------------------------------------------------------------|
| `feed_post_keep`   | yes      | `integer` | The default number of posts to keep in a feed's file, defaults to `10` if not specified |
| `status_post_keep` | yes      | `integer` | The number of posts to keep in the error feed, defaults to `3` if not specified         |
| `debug`            | yes      | `boolean` | If true, extra information about the error will be present in error posts               |
| `feeds`            | no       | `array`   | An array of feed objects, described below                                               |

Feed object properties:

| Property               | Optional | Type      | Description                                                                                                                                   |
|------------------------|----------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `url`                  | no       | `string`  | The url to the site to be scraped                                                                                                             |
| `title`                | no       | `string`  | The title of the rss feed                                                                                                                     |
| `file`                 | no       | `string`  | The file to put the feed in                                                                                                                   |
| `keep_posts`           | yes      | `integer` | How many posts to keep in the feed file, defaults to the value of `feed_post_keep`                                                            |
| `post`                 | no       | `object`  | Configuration for getting post elements from the scraped site                                                                                 |
| `verify_uniqueness`    | yes      | `boolean` | If the post links, in addition to post titles, should be used to verify a new post is present, defaults to `false`                            |
| `guid`                 | yes      | `boolean` | If a `guid` element should automatically be generated for every post (just the same as the permalink), defaults to `true`                     |
| `time_between_queries` | yes      | `object`  | If present, Rssifier will only open a connection to the site once the provided duration has elapsed since Rssifier last connected to the site |
| `days_of_week`         | yes      | `array`   | If present, Rssifier will only open a connection to the site during the provided days                                                         |

### `post` Properties

| Property       | Optional | Type                | Description                                                                                                                                                                                                    |
|----------------|----------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `publish_date` | yes      | `string`            | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the publish date in the scraped site. If not present uses the time Rssifier was run and found a new post |
| `permalink`    | no       | `string`            | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the permanent link to the post. Should point directly to an element with a `href` attribute              |
| `title`        | no       | `string`            | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the title of the post                                                                                    |
| `author`       | yes      | `string`            | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the author of the post. If not present, no author element is added                                       |
| `multi_author` | yes      | `boolean`           | If the `author` selector should match all elements, or just the first matching element                                                                                                                         |
| `description`  | yes      | `string` or `array` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the description of the post. If not present, the title is used                                           |
| `base_query`   | yes      | `string`            | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), prepended to all other selectors with a space character                                                               |

> If the `description` field is an `array`, each query will be searched for and added to the description as-is, supporting non-text elements, such as images.

### `time_between_queries` Properties

| Property  | Optional | Type      | Description                                           |
|-----------|----------|-----------|-------------------------------------------------------|
| `days`    | yes      | `integer` | The day component of the time between site queries    |
| `hours`   | yes      | `integer` | The hour component of the time between site queries   |
| `minutes` | yes      | `integer` | The minute component of the time between site queries |

> **Note**: While all of these are technically optional, at least one of them *must* be present to be valid

### `days_of_week` Values

`days_of_week` is an array of strings, the names of the days of the week on which connections are permitted to be made.

Accepts `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`, and `sunday` case-insensitively.

If not present, connections are permitted any day of the week. If no valid days are found while parsing, an error will be logged and all days will be permitted.

### Usage

Feeds are placed in a directory called `feeds` in the same directory as where Rssifier was invoked

So if Rssifier was run in `C:/Documents/rss/` then the `config.json` file would need to be at `C:/Documents/rss/config.json` and rss feeds would be in `C:/Documents/rss/feeds/`

Additionally, if an `.ico` file with the same name as a feed file is present in the `feeds` directory (i.e. `.../feeds/xkcd.ico` and `.../feeds/xkcd.xml`), it will be added to the feed's channel as an `<image>` element on creation

In this repo there is an example `config.json` which processes [xkcd](https://xkcd.com/), [AMWUA](https://www.amwua.org/)'s blog and news collator, and [AZPM Environment](https://news.azpm.org/environment/)

In addition to the feeds created from the `config.json` file there is a `Rssifier Status` feed that will auto-create after first running Rssifier. This will contain posts about any errors Rssifier encounters while creating/updating feeds.

### Stylization

In its error posts, Rssifier uses colors to signify certain things. These are handles by an inline style tag at the start of every post using the classes `rssifier-r`, `rssifier-p`, `rssifier-g`, and `rssifier-b`.

This exists so that if your feed reader strips/does not allow inline css, but allows user-defined css (i.e. Thunderbird's dark mode), you can still have colors by *some* means.

If your reader does not allow inline css *and* does not support user-provided styles, all text will be the default color.

A table of the classes and their usages

| Class        | Default color | Usage             |
|--------------|---------------|-------------------|
| `rssifier-r` | `red`         | Java stack traces |
| `rssifier-p` | `purple`      | Query clipping    |
| `rssifier-g` | `green`       | In-post JSON      |
| `rssifier-b` | `blue`        | HTML tags         |
