<!--
Listener notes for the next release: what listeners will read in What's new, the app's Coming next
and the release announcement. Every PR that changes something listeners notice edits this list.

- Read the whole list first. If a line already covers the feature, edit that line so it says the net
  change since the last release (black to yellow, not a second "green to yellow" line).
- One line per change, in plain listener words: what they notice, not how it was built. No class
  names, CI or internal terms. The product name is boxlore (lowercase).
- Highlights: at most 3 lines, most important first. They open What's new and form the release
  announcement, which must fit one notification with its title (the PR check measures it). To add
  one when there are 3, move the least important down into its group.
- New, Improved, Fixed: keep lines about the same screen or feature together (show pages, the
  player, Home, Listening history, notifications, sync and backups...). Add a line right after
  the last line about the same screen, or at the end of its group when there is none.
- End a line with the PRs it covers, like (#1234) or (#1234, #1240). Optional.
- Announcement title: optional; without it the announcement reads "boxlore vX.Y.Z is here".

Release preparation keeps these notes as release-notes/vX.Y.Z.md and empties this list.
-->

Announcement title:

## Highlights

1. Listening history has a new Insights tab: your listening time, top shows, when and where you listen, how much of it is new episodes, and your records. (#1207)
2. Each month, Echo tells the story of your last month of listening: your numbers first, how you listened, your records, and something new to say each time, ready to share. (#1207)
3. Subscriptions has a cleaner look, and making a folder takes a few short steps: pick shows yourself, follow one genre, or let Auto make a folder for every genre. (#1198)

## New

- Timeline lets you search your listening history, jump to any day and pick up where you left off. (#1207)
- The Vergecast, Conspiracy Theories and Waveform now have show pages in their own style, marked as a boxlore pick, with the latest episode up top and a short moment when you subscribe. (#1204)
- Show pages can list episodes straight from a show's own feed, keep them for offline viewing, and tell you when boxlore is showing its own episode list instead. Rolling out gradually. (#1184)
- On New Episodes, swipe right to add an episode to your queue or left to mark it played, and pull down to check for new episodes. (#1198)
- Listening in German, French, Spanish, Portuguese, Russian or Dutch? You can now turn English off in Library settings › Languages, and boxlore then adds English shows to recommendations, search and suggestions only when there aren't enough in your languages. (#1187, #1191)
- Free up space with Clear saved shows in Library settings: it removes the episode lists saved for shows you opened but don't follow. (#1192)

## Improved

- Your listening time, longest sessions and where you listened carry over with cloud sync and backups, backups include your whole listening history, and listening on each of your phones counts once. (#1200, #1201, #1207)
- Cloud sync keeps your progress, likes, finished episodes and queue in step across all your devices, including ones on older versions of boxlore, and episodes you remove from the queue stay removed everywhere. (#1179)
- Restoring a library backup keeps your played and liked episodes on the right episodes, and exported OPML files point other apps to each show's own feed. (#1188)
- Subscriptions remembers whether you chose grid or list, and shows you follow later are added to their genre folder automatically. (#1198)
- Show pages load more smoothly, with the page's shape appearing right away and clearer messages while episodes load. (#1196)
- In light mode, the player keeps a light Play icon on a deeper cover colour and uses a soft dark shade of the cover for its text, and its quick-action buttons use cleaner, matching icons. (#1199)
- Shared episode links open the right episode for friends on any version of boxlore, even episodes only in a show's own feed, and keep your plays, progress and downloads on the episode you already have. (#1181)
- Library settings has separate rows for your chart country and your languages, and changing your country keeps your languages. (#1187)
- After importing your library or picking shows in search, choose whether to see similar shows or go straight to Home, and skip to Home anytime while suggestions load. (#1194)
- When boxlore needs a quick check that you're a person before Echo can suggest shows, Echo asks for it in its own voice, and the check fits in with the rest of the screen. (#1205)
- New-episode notifications arrive sooner after a release. (#1151)

## Fixed

- Shows whose feed stopped working or moved to a new address get new episodes and notifications again: boxlore follows the new address, checks the shows you follow after updating, and passes the fix to your other devices through sync. (#1150, #1159, #1160, #1182, #1183)
- New-episode notifications no longer repeat for the same episode when one show's feed misbehaves. (#1202)
- Turning off episode notifications for an RSS feed stops sharing its feed link from your device. Links already shared stay in earlier public backups. (#1140)
- Shows that changed their catalog number stay followed once: no more Subscribe button or duplicate follow, and the episodes you played before the change stay played. (#1186)
- Shows with more than 100 episodes keep progress, history and downloads on their newer episodes, and an episode of one show no longer replaces an episode of another show that uses the same feed. (#1159)
- Shows no longer list the same episode more than once after refreshing. (#1176, #1190)
- Hiding completed episodes no longer empties a show's page: boxlore keeps loading until it finds episodes you haven't finished, and tells you when you've finished them all. (#1196)
- On foldables and tablets, and when Android closes boxlore in the background, a show's search, open dialogs and selected episodes stay where you left them. (#1174)
- Full episodes that a show's feed labels as trailers show as normal episodes and can play next in Smart Queue. (#1204)
- Episodes you queue from another show, or play from Downloads, appear under the right show in the player, even after you restart boxlore. (#1203)
- Downloaded episodes that the Smart Queue adds play from your download. (#1178)
- boxlore no longer crashes on some tablets and foldables when you scroll to the end of the top row on Home. (#1193)
- Marquee hero cards with Artwork or App theme cover colours show the cover clearly instead of a dark blur. (#1195)
- Home and Explore keep the picks and trending shows you already see when boxlore can't reach the server. (#1180)
- Listening insights count every episode you've finished, started or liked, not just recent ones. (#1200)
- If boxlore can't check Google Play for updates, it offers to open its Play Store page so you can update there. (#1197)
- Clearing boxlore's data no longer opens a random episode on the next launch. (#1185)
