# Rssifier

A (relatively) simple java program that can dynamically create local rss feeds from most websites

In all honesty, you probably shouldn't use this

Uses [Jsoup](https://jsoup.org/) to retrieve and parse sites/files and [Gson](https://github.com/google/gson) to parse the `feeds.json` file

Designed to be run by a script one or two times a day and/or on computer startup

Built with Java 21, probably would work with Java 8 or 17, but I haven't tested

## Usage

in the same directory as the jar is *invoked* in, have a `feeds.json` file. This is an array of objects with the following properties:

| Property    | Optional | Type      | Description                                                                                                       |
|-------------|----------|-----------|-------------------------------------------------------------------------------------------------------------------|
| `url`       | no       | `string`  | The url to the site to be scraped                                                                                 |
| `title`     | no       | `string`  | The title of the rss feed                                                                                         |
| `file`      | no       | `string`  | The file to put the feed in                                                                                       |
| `keepPosts` | yes      | `integer` | How many posts to keep in the feed file, defaults to `10`                                                         |
| `post`      | no       | `object`  | Configuration for getting post elements from the scraped site                                                     |
| `guid`      | yes      | `boolean` | If a `guid` should automatically be generated for every post (just the same as the permalink), defaults to `true` |

`post` properties:

| Property      | Optional | Type     | Description                                                                                                                                                                                                      |
|---------------|----------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `publishDate` | yes      | `string` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the publish date in the scraped site. If not present uses the time Rssifier was run and found a new 'post' |
| `permalink`   | no       | `string` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the permanent link to the post.                                                                            |
| `title`       | no       | `string` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the title of the post                                                                                      |
| `author`      | yes      | `string` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the author of the post. If not present, no author element is added                                         |
| `description` | yes      | `string` | A [css-like element selector](https://jsoup.org/apidocs/org/jsoup/select/Selector.html), used to find the description of the post. If not present, the title is used                                             |

Feeds are placed in a directory called `feeds` in the same directory as where Rssifier was invoked

So if Rssifier was run in `C:/Documents/rss/` then the `feeds.json` file would need to be at `C:/Documents/rss/feeds.json` and rss feeds would be in `C:/Documents/rss/feeds/`

Additionally, if an `.ico` file with the same name as a feed file is present in the `feeds` directory (i.e. `.../feeds/xkcd.ico` and `.../feeds/xkcd.xml`), it will be added to the feed's channel as an `<image>` element on creation

In this repo there is an example `feeds.json` which processes [xkcd](https://xkcd.com/) and [The Long Hike](https://thelonghikecomic.com/)

In addition to the feeds created from the `feeds.json` file there is a `Rssifier Status` feed that will auto-create after first running Rssifier that will provide a feed for any errors Rssifier encounters while creating/updating feeds
