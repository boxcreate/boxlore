package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast

/**
 * Editorial video feeds shown on Home.
 *
 * TED is intentionally pinned first. The remaining feeds are in descending editorial
 * popularity order so Home does not need a network request or ranking pass to render them.
 */
internal fun featuredVideoPodcasts(): List<Podcast> = FeaturedVideoPodcastCatalog.podcasts

internal fun featuredTedTalksSdPodcast(): Podcast = FeaturedVideoPodcastCatalog.tedTalksSd

private object FeaturedVideoPodcastCatalog {
    val tedTalksSd =
        videoPodcast(
            id = "588746",
            title = "TED Talks Daily",
            artist = "TED",
            imageUrl = "https://pl.tedcdn.com/rss_feed_images/ted_talks_main_podcast/video-sd.png",
            feedUrl = "https://feeds.feedburner.com/TEDTalks_video",
            description = "Ideas worth spreading, presented by remarkable people from around the world.",
        )

    val podcasts =
        listOf(
            videoPodcast(
                id = "793182",
                title = "TED Talks Daily",
                artist = "TED",
                imageUrl = "https://pl.tedcdn.com/rss_feed_images/ted_talks_main_podcast/video-hd.png",
                feedUrl = "https://feeds.feedburner.com/TedtalksHD",
                description = "Ideas worth spreading, presented by remarkable people from around the world.",
            ),
            videoPodcast(
                id = "314278",
                title = "This Week in Tech",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/This%20Week%20in%20Tech/album_art/hd/TWIT_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/twit_video_hd.xml",
                description = "A roundtable on the week’s biggest technology stories.",
            ),
            videoPodcast(
                id = "202764",
                title = "Security Now",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Security%20Now/album_art/hd/SN_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/sn_video_hd.xml",
                description = "Deep, practical explanations of security, privacy, and cybercrime.",
            ),
            videoPodcast(
                id = "315565",
                title = "MacBreak Weekly",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/MacBreak%20Weekly/album_art/hd/MBW_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/mbw_video_hd.xml",
                description = "Apple news and analysis from veteran observers.",
            ),
            videoPodcast(
                id = "202766",
                title = "Windows Weekly",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Windows%20Weekly/album_art/hd/WW_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/ww_video_hd.xml",
                description = "Microsoft, Windows, cloud, and the wider PC world.",
            ),
            videoPodcast(
                id = "745227",
                title = "iOS Today",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/iOS%20Today/album_art/hd/IOS_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/ipad_video_hd.xml",
                description = "Apps, tips, and ideas for Apple’s mobile devices.",
            ),
            videoPodcast(
                id = "603116",
                title = "Intelligent Machines",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Intelligent%20Machines/album_art/hd/IM_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/twig_video_hd.xml",
                description = "AI, automation, and the machines reshaping everyday life.",
            ),
            videoPodcast(
                id = "1063559",
                title = "Tech News Weekly",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Tech%20News%20Weekly/album_art/hd/TNW_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/tnw_video_hd.xml",
                description = "Interviews with the journalists behind the week’s tech stories.",
            ),
            videoPodcast(
                id = "1059111",
                title = "FLOSS Weekly",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/floss_weekly/album_art/hd/floss2022_albumart_standard_2400.jpg",
                feedUrl = "https://feeds.twit.tv/floss_video_hd.xml",
                description = "The people and projects driving open-source software.",
            ),
            videoPodcast(
                id = "5799184",
                title = "Hands-On Windows",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Hands-On%20Windows/album_art/hd/HOW_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/howin_video_hd.xml",
                description = "Practical Windows guidance, from visual tweaks to performance.",
            ),
            videoPodcast(
                id = "6284816",
                title = "Untitled Linux Show",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/Untitled%20Linux%20Show/album_art/hd/ULS_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/uls_video_hd.xml",
                description = "Linux news for desktop, gaming, servers, and enterprise.",
            ),
            videoPodcast(
                id = "166546",
                title = "Know How…",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/" +
                    "public/images/shows/know_how./album_art/hd/kh1400video.jpg",
                feedUrl = "https://feeds.twit.tv/kh_video_hd.xml",
                description = "Hands-on projects, gaming hardware, and practical technology.",
            ),
            videoPodcast(
                id = "394022",
                title = "TWiT Throwback",
                artist = "TWiT",
                imageUrl = "https://twit.cachefly.net/coverart/throwback/throwback2048video.jpg",
                feedUrl = "https://feeds.twit.tv/throwback_video_large.xml",
                description = "A time machine through ten years of technology history.",
            ),
            videoPodcast(
                id = "760859",
                title = "TWiT Events",
                artist = "TWiT",
                imageUrl =
                "https://elroy.twit.tv/sites/default/files/styles/twit_album_art_2048x2048/public/" +
                    "images/shows/TWiT%20Events/album_art/hd/TWITevents_albumart_standard_video.jpg",
                feedUrl = "https://feeds.twit.tv/events_video_hd.xml",
                description = "On-location coverage from major technology events.",
            ),
        )
}

private fun videoPodcast(
    id: String,
    title: String,
    artist: String,
    imageUrl: String,
    feedUrl: String,
    description: String,
): Podcast = Podcast(
    id = id,
    title = title,
    artist = artist,
    imageUrl = imageUrl,
    description = description,
    genre = "Technology",
    medium = "video",
    feedUrl = feedUrl,
)
