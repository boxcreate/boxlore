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

- boxlore is now on Wear OS. Play, stream and download episodes on your watch, with or without your phone. (#1256, #1258)

## New

- Send to watch on an episode's page downloads it to your watch. (#1258)
- A tile and a watch-face shortcut resume your last episode in one tap. (#1258)
- Sign in on your watch with one tap from your phone. (#1258)
- On your watch, a show's page now says when its episodes are loading or couldn't load, with Try again, and gets a fresh list from your phone when the one it has is old.
- Hide a show everywhere from the menu on its page. boxlore stops suggesting it on Home, Explore, Lore, Smart Queue and Android Auto. Unhide it from the same menu or in LeaveMeAlone.
- Give a show its own speed in Playback for this show, in the menu on its page. After that, changing the speed in the player while that show plays updates its own speed.
- Trim silence skips quiet gaps in episodes. Turn it on in Settings › Playback or the player's speed controls, or just for one show in Playback for this show. It doesn't work while casting.
- Episode titles only shows a show's episodes as a short list of titles. Turn it on from the menu on a show's page or in Settings › Appearance › Show pages.
- Archive episodes you don't plan to hear, like trailers, from a show's page or an episode's page. They leave your lists without being marked as played, and you can bring them back any time.
- Make playlists in Library from the shows you follow, like one for comedy and one for history. Each keeps its place, picks up new episodes by itself, and you can switch between them any time.

## Improved

- Sharing a show or episode now sends a card that looks like the boxlore player, in colours from its artwork, with the episode's length and date or how often the show posts, and the share sheet shows the card before you send it. Episodes shared from a moment show where they start, and the message is now just the name and the link.

- Home screen widgets have a new look with rounder shapes. The players now show how far you are and how much time is left, and the skip buttons show your own skip lengths.
- The Your Shows widget is now a grid of artwork. Choose whole covers or shapes in Settings › Appearance › Widgets.
- See every boxlore widget and add it to your home screen from Settings › Appearance › Widgets.
- On Home, a show you listen to oldest first now picks up exactly where you left off, even deep into a long series, and skips trailers. Daily mix suggests the same next episode.
- Android Auto has a new layout. Home shows what you're midway through, then large blocks for New episodes, Your shows, Daily mix, Downloads and more, with Continue, New and Shows one tap away as tabs.
- In Android Auto, tapping an episode in New episodes, Daily mix, Downloads, Liked or Drive picks keeps playing down that list, and an episode you resume is followed by your queue.
- Android Auto lists now update when you queue, download, like or follow something on your phone, and opening New episodes or Shows in the car checks your shows for new episodes.
- In Android Auto, your last episode is ready on the car's screen as soon as you connect, so one tap resumes it.
- If you installed boxlore from GitHub or another store, it now shows you the one Android Auto setting that puts it on your car's screen, the first time you use it in the car.

## Fixed

- Widgets that follow your phone's light and dark mode now switch with it, even when boxlore isn't open.
- The Your Shows and New Episodes widgets now count your whole library instead of stopping at 50.
- While an episode plays, the Your Shows and New Episodes widgets and your Library use less battery.
- While an episode plays, boxlore syncs your place in it every few minutes instead of every few seconds, so it uses less battery and data. Pausing, switching episodes or leaving boxlore still syncs it straight away.
- With a large listening history, boxlore no longer slows down or closes when your phone gets its connection back.
- On Home, a show you've heard every episode of now lists its latest episodes instead of an empty list, and a show whose episodes can't load lets you try again.
- On Home, tapping a show in Your shows now checks it for new episodes, even if boxlore checked it a few hours ago.
- In Android Auto, artwork loads faster, and every episode has a picture, in lists and in the queue: one with no image of its own shows its show's cover.
- In Android Auto, tapping something that can't be played no longer stops what you're listening to.
- In Android Auto, skipping forward or back no longer sends the list you're browsing back to the top.
- In Android Auto, Shows no longer stops at 50 of the shows you follow.
- Casting no longer closes boxlore when your queue has an episode your Cast device can't stream, such as one from a feed on your home network. Those episodes stay in your queue but are skipped on the Cast device.
- After you listen on another device, Play on the mini player now starts the episode it shows, at the right spot, instead of an older one from your queue.
- When boxlore fills your queue from the show you're playing, or offers to continue it, it now picks the episodes right after yours, even deep into a long show you don't follow, instead of jumping hundreds of episodes ahead.
- Downloaded episodes now show their chapters and transcript straight away, even offline or on a weak connection. Chapters and transcripts that can't load now stop trying instead of loading forever.
- Smart Downloads no longer downloads episodes you've already finished, a show you listen to oldest first gets its next episode, and Sync Now checks your shows for new episodes before it picks what to download.
- A download you start that fails now stays in Downloads, marked as failed, with Download Again, instead of disappearing.
- Auto-downloads checked in the background stay on schedule when one show's feed can't be read, so new episodes from your other shows still arrive on time.
- For you in Explore no longer reloads again and again while your account syncs. It refreshes when the shows you follow or your languages change, and when your picks get old.
- When a show search or Explore's Top chart can't load, boxlore now says so and lets you try again, instead of saying no shows were found or ending the chart early. This includes searching for shows while you set up boxlore.
- Picked for you on Home and For you in Explore now start from the shows you've played or followed most recently, not the first ones from A to Z, and leave out shows you follow, even in a large library.
- In Lore, Check for new cards now looks much further through the deck, so it finds new cards past the first few pages.
- If you follow a lot of shows, New Episodes no longer misses new episodes from some of them, and pulling down on New Episodes checks all your shows (up to 200), not just a few.
- Some shows listed their episodes without dates and out of order, said they had ended, and their new episodes never reached New Episodes. They now show their dates, newest first, and new episodes arrive as usual.
- The Brief opens as soon as it's ready, even when its stories are slow to load. They appear when they arrive.
- New episode notifications from different shows no longer replace each other or open the wrong show. Each show gets its own notification, grouped together, for up to 16 shows at a time.
- On a show's page, Mark all as played and Mark all as unplayed now change every episode, not just the newest 200. If the episodes can't load, nothing changes and boxlore tells you.
- Marking a whole show as played no longer makes episodes of your other shows, or ones you played long ago, look unplayed or lose their progress, on your phone or in Android Auto. Episodes you'd already finished stay in Listening history, the show no longer jumps to the top of Your shows or Most listened, and your recommendations keep following what you listen to. Marking the show unplayed again no longer fills today's Listening history with episodes you never played.
- On a show's page where you allowed feed checks, a check that fails now says Couldn't update instead of Up to date, and pulling down tells you when the feed couldn't be read.
- On a show's page, adding selected episodes to your queue now adds as many as fit and tells you when your queue is full, instead of silently leaving the rest out.
- When boxlore can't read a long show's own feed, searching that show now looks through up to 11,000 of its episodes instead of the newest 1,000, and says so when there are more. If the search can't run, the page says so and lets you try again instead of showing no matches.
- On a show page, episodes that don't load are tried again and then show Try again, instead of the list just ending. Select all on a long show selects every episode or says it couldn't.
- After you sign in on a new phone, every show you follow now gets its name and artwork. Some used to stay as “Loading...” with no artwork.
- Restoring a full library backup no longer drops shows you added by RSS feed when their feed can't be reached. They come back with your listening history, and their episodes load once the feed responds.
- Importing a large library from another podcast app no longer stops partway. Shows boxlore can't add right away are added later while boxlore is open.
