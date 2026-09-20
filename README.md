<div align="center" id="top">

<img src="docs/images/featured_banner5.png" width="960" alt="boxlore screens showing podcast playback, home, and lore discovery"/>

<br/><br/>

## Podcasts, done better

<p align="center">
Search by name or topic, get recommendations, listen offline, watch video shows, and keep up with new episodes.
</p>

<br/>

<p>
<!-- download-play:start -->
<a href="https://play.google.com/store/apps/details?id=cx.aswin.boxlore"><img src="docs/images/button_playstore_v8.svg" width="224" height="60" alt="Get boxlore on Google Play"/></a>
<!-- download-play:end -->
&nbsp;&nbsp;
<!-- download-apk:start -->
<a href="https://github.com/boxcreate/boxlore/releases/latest/download/boxlore-v0.0.25.apk"><img src="docs/images/button_github_v8.svg" width="224" height="60" alt="Download the boxlore APK from GitHub"/></a>
<!-- download-apk:end -->
&nbsp;&nbsp;
<!-- download-obtainium:start -->
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/boxcreate/boxlore"><img src="docs/images/button_obtainium_v8.svg" width="224" height="60" alt="Add boxlore to Obtainium"/></a>
<!-- download-obtainium:end -->
</p>

<p>
<a href="https://github.com/boxcreate/boxlore/releases/latest"><img src="https://img.shields.io/github/v/release/boxcreate/boxlore?style=flat-square&amp;label=GitHub%20release&amp;color=6750A4" alt="GitHub latest release"/></a>
<a href="https://play.google.com/store/apps/details?id=cx.aswin.boxlore"><img src="https://img.shields.io/endpoint?url=https%3A%2F%2Fplay.rajkumaar.co.in%2Fversion%3Fid%3Dcx.aswin.boxlore&amp;label=Play%20Store%20version&amp;color=6750A4&amp;style=flat-square" alt="Google Play Store version"/></a>
<img src="https://img.shields.io/github/downloads/boxcreate/boxlore/total?style=flat-square&logo=github&logoColor=white&color=6750A4" alt="Total GitHub downloads"/>
<a href="LICENSE"><img src="https://img.shields.io/badge/License-PolyForm%20Strict-4F378B?style=flat-square&logo=lock&logoColor=EADDFF" alt="PolyForm Strict License"/></a>
</p>

<br/>

**[About](#about)** ·
**[The soul of boxlore](docs/SOUL.md)** ·
**[What we track](docs/PRIVACY.md)** ·
**[Features](#features)** ·
**[Release notes](#release-notes)** ·
**[Screenshots](#screenshots)** ·
**[Install](#install)**

<br/><br/>

<img src="docs/images/m3/divider.svg" width="820" alt=""/>

</div>

<br/>

## About

<a id="about"></a>

boxlore is an Android app for finding, following, and playing podcasts. Search for a show even when the spelling is imperfect, or describe a subject to find relevant shows and episodes.

You can listen online or offline, build a queue, and keep subscriptions, downloads, history, and likes in one library.

### How discovery and recommendations work
Unlike mainstream platforms that use centralized engagement algorithms to maximize screen time, boxlore uses a two-stage hybrid architecture:
* **Server-side semantic matching:** To find relevant candidates across a vast catalog, the app sends bounded listening seeds (recent shows you completed, liked, or engaged with) to our servers to run semantic vector search against our podcast index.
* **On-device taste tuning:** The candidate shows returned by the server are then re-ranked locally on your phone using an on-device learning model that adapts to your skip habits and time of day—without building a persistent cloud advertising profile.

Read **[The soul of boxlore](docs/SOUL.md)**: why this app is free, ad-free, and crafted with care.

See **[What we track and why](docs/PRIVACY.md)**: full technical transparency into our requests and telemetry.

## Features

<a id="features"></a>

| Feature | What it does |
| :--- | :--- |
| **Find podcasts** | Search by show name, even with a misspelling, or describe a topic to find podcasts and individual episodes about it. |
| **Recommendations** | Hybrid discovery: Server-side semantic vector search using your recent listening seeds, with on-device tuning for your playback habits. |
| **Automatic queue** | When the list of episodes waiting to play runs low, Smart Queue can add more from the current show, unfinished listening, subscriptions, and recommendations. |
| **Audio and video player** | Play audio and video episodes with speed controls, a sleep timer, chapters, transcripts, intro skipping, and a queue you can reorder. |
| **Downloads and library** | Keep subscriptions, new episodes, downloads, likes, and listening history together. Smart Downloads can prepare episodes for offline listening. |
| **Alerts and widgets** | Choose which shows can send new-episode alerts. Home-screen widgets show playback controls, subscriptions, or new episodes. |
| **TV and car playback** | Send playback to supported TVs and speakers with Google Cast, or listen through Android Auto. |
| **More ways to discover** | Browse short question cards that lead to related episodes, or play an optional daily news briefing with sources and chapters. |
| **Import and backup** | Import a subscription file (OPML) from another podcast app, then export your subscriptions or a full library backup whenever needed. |
| **No app ads** | boxlore has no banner ads and does not charge to unlock features. Podcasts may still contain ads placed by their publishers. |

<!-- upcoming-changes:start -->

## Release notes

### Upcoming

<!-- release-upcoming:start -->
New features and improvements for the next release are currently in development.
<p align="center"><sub><sub>AI-generated summary; may contain mistakes.<br/>Verify details in the <a href="CHANGELOG.md">changelog</a> and linked pull requests.</sub></sub></p>
<!-- release-upcoming:end -->


### What's New · `v0.0.25` · 2026-09-08

<!-- release-whats-new:start -->
<!-- release-meta: version=v0.0.25 date=2026-09-08 -->


<b>🆕 New features:</b>
<ul align="left">
<li><b>Subscription Folders & Custom Sizes</b>: Organize your podcast library into custom folders with adaptive layouts—from compact 1×1 tiles to full-width 3×1 shelves and expanded panels, featuring direct show playback right from the folder card. <a href="https://github.com/boxcreate/boxlore/pull/1059"><img src="https://img.shields.io/badge/PR-1059-6750A4?style=flat-square" alt="PR #1059" height="18"/></a></li>
<li><b>Auto-Organize Library</b>: Instantly group your subscribed shows into genre-based folders with one tap, smart size presets, and automatic deduplication. <a href="https://github.com/boxcreate/boxlore/pull/1059"><img src="https://img.shields.io/badge/PR-1059-6750A4?style=flat-square" alt="PR #1059" height="18"/></a></li>
<li><b>Custom Podcast Tags & Icons</b>: Assign custom tags and expressive icons to any subscribed show in boxlore, with live chip preview, keyword suggestions, and priority filtering in Subscriptions. <a href="https://github.com/boxcreate/boxlore/pull/1055"><img src="https://img.shields.io/badge/PR-1055-6750A4?style=flat-square" alt="PR #1055" height="18"/></a> <a href="https://github.com/boxcreate/boxlore/pull/1059"><img src="https://img.shields.io/badge/PR-1059-6750A4?style=flat-square" alt="PR #1059" height="18"/></a></li>
<li><b>Offline Chapters & Transcripts</b>: Downloaded episodes now include Podcasting 2.0 chapters and synced transcripts for offline listening on planes, trains, and commutes. <a href="https://github.com/boxcreate/boxlore/pull/1065"><img src="https://img.shields.io/badge/PR-1065-6750A4?style=flat-square" alt="PR #1065" height="18"/></a></li>
<li><b>Full Backup & Restore</b>: Export and restore your complete library configuration—including folder definitions, show memberships, sorting preferences, and custom tags—with 100% fidelity. <a href="https://github.com/boxcreate/boxlore/pull/1050"><img src="https://img.shields.io/badge/PR-1050-6750A4?style=flat-square" alt="PR #1050" height="18"/></a> <a href="https://github.com/boxcreate/boxlore/pull/1059"><img src="https://img.shields.io/badge/PR-1059-6750A4?style=flat-square" alt="PR #1059" height="18"/></a></li>
<li><b>Recommendation Variation</b>: Time-of-day rotation and cross-day variety for the &ldquo;Because you like&rdquo; recommendation rail on the Home screen. <a href="https://github.com/boxcreate/boxlore/pull/1063"><img src="https://img.shields.io/badge/PR-1063-6750A4?style=flat-square" alt="PR #1063" height="18"/></a></li>
<li><b>Same-Show Continuation Banner</b>: Added a convenient queue banner to preview and add upcoming forward episodes of a show when played from recommendations. <a href="https://github.com/boxcreate/boxlore/pull/1019"><img src="https://img.shields.io/badge/PR-1019-6750A4?style=flat-square" alt="PR #1019" height="18"/></a></li>
</ul>
<b>⚡ Enhancements to existing features:</b>
<ul align="left">
<li><b>Multi-Level Sorting & Drag-and-Drop</b>: Customize folder order with drag-and-drop manual arrangement, smart recency rankings, and independent intra-folder show sorting. <a href="https://github.com/boxcreate/boxlore/pull/1059"><img src="https://img.shields.io/badge/PR-1059-6750A4?style=flat-square" alt="PR #1059" height="18"/></a></li>
<li><b>Inline Player Transcript Theming</b>: Polished inline player transcript view with theme-matching headers, dynamic controls, and calibrated text sizing. <a href="https://github.com/boxcreate/boxlore/pull/1065"><img src="https://img.shields.io/badge/PR-1065-6750A4?style=flat-square" alt="PR #1065" height="18"/></a></li>
<li><b>Download Removal Confirmation</b>: Added a confirmation prompt before removing downloaded episodes across show details and player sheets to prevent accidental deletions. <a href="https://github.com/boxcreate/boxlore/pull/1064"><img src="https://img.shields.io/badge/PR-1064-6750A4?style=flat-square" alt="PR #1064" height="18"/></a></li>
<li><b>Backup Restore Progress & Permissions</b>: Added real-time show progress and active animated loader when restoring library backups, plus a streamlined notification permission check. <a href="https://github.com/boxcreate/boxlore/pull/1050"><img src="https://img.shields.io/badge/PR-1050-6750A4?style=flat-square" alt="PR #1050" height="18"/></a></li>
<li><b>Subscriptions Tab Style Preference</b>: Added tab style preference in Settings &gt; Appearance, allowing users to choose between top header tabs or a bottom floating selector in boxlore. <a href="https://github.com/boxcreate/boxlore/pull/1048"><img src="https://img.shields.io/badge/PR-1048-6750A4?style=flat-square" alt="PR #1048" height="18"/></a></li>
<li><b>Memory & Parsing Efficiency</b>: Resolved out-of-memory errors during large podcast pagination and feed parsing by streaming responses and bounding in-memory cache sizes. <a href="https://github.com/boxcreate/boxlore/pull/1044"><img src="https://img.shields.io/badge/PR-1044-6750A4?style=flat-square" alt="PR #1044" height="18"/></a></li>
<li><b>Background Download Reliability</b>: Improved background auto-download reliability so new episodes are ready for offline listening as soon as notifications arrive. <a href="https://github.com/boxcreate/boxlore/pull/1033"><img src="https://img.shields.io/badge/PR-1033-6750A4?style=flat-square" alt="PR #1033" height="18"/></a></li>
<li><b>Show Refresh Integrity</b>: Pulling down to refresh on a podcast&rsquo;s page now checks for newly released episodes without resetting notification or download settings. <a href="https://github.com/boxcreate/boxlore/pull/1022"><img src="https://img.shields.io/badge/PR-1022-6750A4?style=flat-square" alt="PR #1022" height="18"/></a></li>
</ul>
<b>🐛 Bug fixes:</b>
<ul align="left">
<li><b>Android Auto Queue & Playback Resumption</b>: Android Auto now seamlessly resumes your last played podcast and queue when reconnecting in your vehicle instead of showing an error screen. <a href="https://github.com/boxcreate/boxlore/pull/1045"><img src="https://img.shields.io/badge/PR-1045-6750A4?style=flat-square" alt="PR #1045" height="18"/></a></li>
<li><b>Android Auto Metadata</b>: Fixed an issue where episodes played from Android Auto showed missing show names and could cause playback to resume an older session on restart. <a href="https://github.com/boxcreate/boxlore/pull/1036"><img src="https://img.shields.io/badge/PR-1036-6750A4?style=flat-square" alt="PR #1036" height="18"/></a></li>
<li><b>Android Auto Disconnect Crash</b>: Fixed an intermittent crash that could occur when disconnecting from Android Auto during artwork loading. <a href="https://github.com/boxcreate/boxlore/pull/1041"><img src="https://img.shields.io/badge/PR-1041-6750A4?style=flat-square" alt="PR #1041" height="18"/></a></li>
<li><b>Android 14+ Auto-Download Crash</b>: Fixed an issue on Android 14 and newer where background auto-downloads could crash or fail to start when new episode notifications arrived. <a href="https://github.com/boxcreate/boxlore/pull/1033"><img src="https://img.shields.io/badge/PR-1033-6750A4?style=flat-square" alt="PR #1033" height="18"/></a></li>
<li><b>Download Disk Storage Reclamation</b>: Fixed storage cleanup so deleting or replacing downloaded episodes properly reclaims device disk space. <a href="https://github.com/boxcreate/boxlore/pull/1033"><img src="https://img.shields.io/badge/PR-1033-6750A4?style=flat-square" alt="PR #1033" height="18"/></a></li>
<li><b>Notification Spike Playback Block</b>: Resolved an issue where receiving many episode notifications over time could cause audio playback to fail to start. <a href="https://github.com/boxcreate/boxlore/pull/1034"><img src="https://img.shields.io/badge/PR-1034-6750A4?style=flat-square" alt="PR #1034" height="18"/></a></li>
<li><b>Duplicate Key Crashes</b>: Fixed crashes when viewing search results and recommendation shelves with duplicate items. <a href="https://github.com/boxcreate/boxlore/pull/1043"><img src="https://img.shields.io/badge/PR-1043-6750A4?style=flat-square" alt="PR #1043" height="18"/></a></li>
<li><b>Home Recommendation Tap Crash</b>: Fixed an issue where tapping recommended episodes from the Home screen could cause boxlore to unexpectedly close. <a href="https://github.com/boxcreate/boxlore/pull/1030"><img src="https://img.shields.io/badge/PR-1030-6750A4?style=flat-square" alt="PR #1030" height="18"/></a></li>
<li><b>Queue Continuation Skip</b>: Fixed an issue where the queue would skip upcoming episodes of the show you were listening to. <a href="https://github.com/boxcreate/boxlore/pull/1019"><img src="https://img.shields.io/badge/PR-1019-6750A4?style=flat-square" alt="PR #1019" height="18"/></a></li>
<li><b>Playback Restoration & Widget Crash</b>: Fixed background crashes when restoring playback sessions or updating widgets. <a href="https://github.com/boxcreate/boxlore/pull/1042"><img src="https://img.shields.io/badge/PR-1042-6750A4?style=flat-square" alt="PR #1042" height="18"/></a></li>
<li><b>Video Spotlight Animation</b>: Fixed Video Spotlight cards on the Home screen so their dropshadow and border scale smoothly with the artwork during tap animation instead of revealing a static outer outline. <a href="https://github.com/boxcreate/boxlore/pull/1046"><img src="https://img.shields.io/badge/PR-1046-6750A4?style=flat-square" alt="PR #1046" height="18"/></a></li>
<li><b>Player Cast Icon Theming</b>: Fixed the Cast icon in the full-screen player so it adapts to light and dark themes and matches the Share button. <a href="https://github.com/boxcreate/boxlore/pull/1039"><img src="https://img.shields.io/badge/PR-1039-6750A4?style=flat-square" alt="PR #1039" height="18"/></a></li>
</ul>
<!-- release-whats-new:end -->

<!-- upcoming-changes:end -->

## Screenshots

<a id="screenshots"></a>

<div align="center">
<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/images/homescreen.png" width="240" alt="boxlore Home with listening suggestions and Your Shows"/><br/>
      <b>Home</b><br/><sub>Continue listening and see suggestions</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/semantic_search.png" width="240" alt="boxlore search showing topic-based podcast results"/><br/>
      <b>Search</b><br/><sub>Find a name or explore a topic</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/player.png" width="240" alt="boxlore full podcast player with playback and queue controls"/><br/>
      <b>Player</b><br/><sub>Controls, chapters, transcripts, and queue</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/images/recommendation_engine.png" width="240" alt="boxlore personalized podcast recommendations"/><br/>
      <b>Recommendations</b><br/><sub>Suggestions based on what you listen to</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/library.png" width="240" alt="boxlore Library with subscriptions, likes, downloads, and history"/><br/>
      <b>Library</b><br/><sub>Subscriptions, downloads, likes, and history</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/daily_brief.png" width="240" alt="boxlore daily news briefing with stories and region controls"/><br/>
      <b>Daily briefing</b><br/><sub>Optional news audio with sources</sub>
    </td>
  </tr>
</table>
</div>

## Install

<a id="install"></a>

- **[Google Play](https://play.google.com/store/apps/details?id=cx.aswin.boxlore)** is the recommended install.
- **[GitHub Releases](https://github.com/boxcreate/boxlore/releases/latest)** provides the latest APK for sideloading.
- **[Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/boxcreate/boxlore)** adds boxlore with automatic update tracking.

Android may ask you to allow installation from your browser or file manager when sideloading.

## Project

[Report a bug](https://github.com/boxcreate/boxlore/issues) ·
[Share an idea](https://github.com/boxcreate/boxlore/discussions) ·
[View the roadmap](https://github.com/orgs/boxcreate/projects/2)

boxlore is source-available under the [PolyForm Strict License 1.0.0](LICENSE). Noncommercial use is allowed; redistribution and derivative works are restricted by the license.

<div align="center">

<img src="docs/images/m3/divider.svg" width="820" alt=""/>

**[⬆ Back to top](#top)**

</div>
