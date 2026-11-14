package cx.aswin.boxlore.wear.preview

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow

/** Sample shows and episodes for debug builds. Artwork loads from the shows' public cover art. */
internal object PreviewCatalog {
    private const val ART = "https://is1-ssl.mzstatic.com/image/thumb"
    private const val DAY = 86_400_000L

    /** 10 November 2026, so the sample dates read the same on every run. */
    private const val TODAY = 1_794_268_800_000L

    private fun show(id: String, title: String, publisher: String, art: String, new: Int = 0, subscribed: Boolean = true) =
        WatchShow(id, title, publisher, "$ART/$art/300x300bb.jpg", new, subscribed)

    val shows: List<WatchShow> =
        listOf(
            show("darknet", "Darknet Diaries", "Jack Rhysider", "Podcasts122/v4/3a/80/a7/3a80a7db-5620-f77b-9935-016e61cc2fbc/mza_9399859904175514567.jpg"),
            show("trih", "The Rest Is History", "Goalhanger", "Podcasts211/v4/12/e4/4e/12e44e6e-f250-0621-75c4-668fda6c48a3/mza_9347628783012573828.jpg", new = 1),
            show("daily", "The Daily", "The New York Times", "Podcasts221/v4/ab/64/66/ab6466a9-9a7d-e20e-7a3d-bc5be37d29ce/mza_15084852813176276273.jpg", new = 2),
            show("radiolab", "Radiolab", "WNYC Studios", "Podcasts211/v4/2b/b2/4d/2bb24d28-f3bb-916f-6bf3-9e125ba5219b/mza_4476298389845914795.jpg"),
            show("waveform", "Waveform: The MKBHD Podcast", "MKBHD", "Podcasts221/v4/92/dd/8e/92dd8eed-43ec-dc47-50ae-c1ec7c0a3353/mza_3861776104886813687.jpg"),
            show("hardfork", "Hard Fork", "The New York Times", "Podcasts221/v4/de/c5/20/dec52092-6be0-9007-875c-6aa8e690a905/mza_12490014444602578825.jpg", subscribed = false),
            show("serial", "Serial", "Serial Productions & The New York Times", "Podcasts221/v4/9a/fb/87/9afb8760-0e05-2b3e-24a2-7e14cce74570/mza_14816055607064169808.jpg"),
            show("99pi", "99% Invisible", "Roman Mars", "Podcasts211/v4/79/d0/35/79d035ea-9043-b43e-7380-33cd47bd968b/mza_2606971010425550919.jpg"),
            show("acquired", "Acquired", "Ben Gilbert and David Rosenthal", "Podcasts211/v4/a8/e1/de/a8e1deff-9f88-4e55-a541-b0dc793c0cdc/mza_11539673419613154037.jpg", subscribed = false),
            show("planetmoney", "Planet Money", "NPR", "Podcasts211/v4/85/df/53/85df5334-0fae-28a9-2bc4-b97b81061d0e/mza_10839245066228881011.jpg"),
            show("sysk", "Stuff You Should Know", "iHeartPodcasts", "Podcasts221/v4/aa/82/91/aa82912f-23ee-6f6a-583c-a4e993164d0e/mza_12111158076643383507.jpg"),
            show("vergecast", "The Vergecast", "The Verge", "Podcasts211/v4/1d/64/cd/1d64cd45-4497-ee37-0e35-df108046d9e6/mza_781548057726205094.jpg", new = 1),
            show("searchengine", "Search Engine", "PJ Vogt", "Podcasts211/v4/90/5b/62/905b6220-b426-6e2d-b97c-9f0a16ed31c4/mza_1372109499010593679.jpeg", subscribed = false),
            show("freakonomics", "Freakonomics Radio", "Freakonomics Radio + Stitcher", "Podcasts115/v4/f7/0c/c5/f70cc540-ce36-d96f-b111-c970aad5505c/mza_17703422762227531425.jpg"),
            show("huberman", "Huberman Lab", "Scicomm Media", "Podcasts221/v4/9a/d3/19/9ad31912-0b5a-a16e-2d7c-9fd074698b9c/mza_8994222203629500925.jpg"),
            show("lex", "Lex Fridman Podcast", "Lex Fridman", "Podcasts211/v4/ea/42/d7/ea42d7b1-8d4e-dd97-da5d-c508756550ca/mza_16782152345430036863.jpg"),
            show("sciencevs", "Science Vs", "Spotify Studios", "Podcasts211/v4/62/15/18/621518e5-8eb3-392b-25a7-e7444aab86b6/mza_815589518502039493.jpg"),
            show("hiddenbrain", "Hidden Brain", "Hidden Brain, Shankar Vedantam", "Podcasts221/v4/e2/18/b8/e218b838-b790-3a33-441f-c3772d9abbbf/mza_6896085647855199484.jpg"),
            show("sherlock", "Sherlock & Co.", "Goalhanger", "Podcasts221/v4/2c/1e/1a/2c1e1abf-d431-7b08-b8bd-f9018f5e9004/mza_812454313987963489.jpg"),
        )

    private val byId = shows.associateBy { it.id }

    private fun episode(
        showId: String,
        number: Int,
        title: String,
        minutes: Int,
        daysAgo: Int,
        left: Int? = null,
        download: WatchDownload = WatchDownload.None,
    ): WatchEpisode {
        val show = byId.getValue(showId)
        return WatchEpisode(
            id = "$showId-$number",
            showId = showId,
            title = title,
            showTitle = show.title,
            artworkUrl = show.artworkUrl,
            durationMinutes = minutes,
            releasedAtMillis = TODAY - daysAgo * DAY,
            minutesLeft = left,
            download = download,
        )
    }

    val episodes: List<WatchEpisode> =
        listOf(
            episode("darknet", 1, "Blackshades", 39, 7, left = 26, download = WatchDownload.Done),
            episode("darknet", 2, "Ricky", 80, 21),
            episode("darknet", 3, "Conti", 59, 35),
            episode("trih", 1, "The Charge of the Light Brigade: Outbreak of The Crimean War (Part 1)", 70, 1, left = 42),
            episode("trih", 2, "Revolution in the Soviet Empire: Revenge of the Red Army (Part 2)", 71, 5),
            episode("trih", 3, "Revolution in the Soviet Empire: The Hungarian Uprising (Part 1)", 87, 8),
            episode("daily", 1, "Secrets, Chips and Spycraft: The Nvidia C.E.O. Scandal", 32, 0, left = 12),
            episode("daily", 2, "After a Crushing Defeat, Trump Doubles Down", 31, 1, download = WatchDownload.Done),
            episode("radiolab", 1, "More Perfect: Cruel and Unusual", 56, 4, left = 31),
            episode("radiolab", 2, "The Buried Bodies Case", 49, 11),
            episode("radiolab", 3, "Octomom", 34, 18, download = WatchDownload.InProgress(0.6f)),
            episode("waveform", 1, "Instagram Wants to be YouTube Now?", 100, 4),
            episode("waveform", 2, "Is The \"PROLED\" Macbook Any Good?", 122, 11),
            episode("serial", 1, "METRO - Heavy is the Head that Refuses to Share the Crown", 49, 5),
            episode("serial", 2, "METRO - Karat and Schtick", 40, 11, download = WatchDownload.Done),
            episode("vergecast", 1, "The Vergecast", 95, 2),
        )

    val jumpBackInIds = listOf("trih-1", "daily-1", "radiolab-1")
    val newEpisodeIds = listOf("daily-1", "daily-2", "trih-1", "vergecast-1")
    val upNextIds = listOf("serial-2", "waveform-2", "radiolab-2")
    const val NOW_PLAYING_ID = "darknet-1"

    /** Shows offered for any search, standing in for the search service. */
    val searchPool: List<String> = listOf("hardfork", "vergecast", "waveform", "acquired", "searchengine")
}
