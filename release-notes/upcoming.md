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

## Improved

- Sharing a show or episode now sends a card that looks like the boxlore player, in colours from its artwork, with the episode's length and date or how often the show posts, and the share sheet shows the card before you send it. Episodes shared from a moment show where they start, and the message is now just the name and the link.

- Home screen widgets have a new look with rounder shapes. The players now show how far you are and how much time is left, and the skip buttons show your own skip lengths.
- The Your Shows widget is now a grid of artwork. Choose whole covers or shapes in Settings › Appearance › Widgets.
- See every boxlore widget and add it to your home screen from Settings › Appearance › Widgets.
- On Home, a show you listen to oldest first now picks up exactly where you left off, even deep into a long series, and skips trailers. Daily mix suggests the same next episode.
- Android Auto has a new layout. Home shows what you're midway through, then large blocks for New episodes, Your shows, Daily mix, Downloads and more, with Continue, New and Shows one tap away as tabs.
- In Android Auto, tapping an episode in New episodes, Daily mix, Downloads, Liked or Drive picks keeps playing down that list, and an episode you resume is followed by your queue.
- Android Auto lists now update when you queue, download, like or follow something on your phone.
- In Android Auto, your last episode is ready on the car's screen as soon as you connect, so one tap resumes it.
- If you installed boxlore from GitHub or another store, it now shows you the one Android Auto setting that puts it on your car's screen, the first time you use it in the car.

## Fixed

- Widgets that follow your phone's light and dark mode now switch with it, even when boxlore isn't open.
- The Your Shows and New Episodes widgets now count your whole library instead of stopping at 50.
- While an episode plays, the Your Shows and New Episodes widgets and your Library use less battery.
- On Home, a show you've heard every episode of now lists its latest episodes instead of an empty list, and a show whose episodes can't load lets you try again.
- In Android Auto, artwork loads faster, and every episode has a picture, in lists and in the queue: one with no image of its own shows its show's cover.
- In Android Auto, tapping something that can't be played no longer stops what you're listening to.
- In Android Auto, skipping forward or back no longer sends the list you're browsing back to the top.
- In Android Auto, Shows no longer stops at 50 of the shows you follow.
- Casting no longer closes boxlore when your queue has an episode your Cast device can't stream, such as one from a feed on your home network. Those episodes stay in your queue but are skipped on the Cast device.
- After you listen on another device, Play on the mini player now starts the episode it shows, at the right spot, instead of an older one from your queue.
- When boxlore fills your queue from the show you're playing, or offers to continue it, it now picks the episodes right after yours, even deep into a long show you don't follow, instead of jumping hundreds of episodes ahead.
- Downloaded episodes now show their chapters and transcript straight away, even offline or on a weak connection. Chapters and transcripts that can't load now stop trying instead of loading forever.
- For you in Explore no longer reloads again and again while your account syncs. It refreshes when the shows you follow or your languages change, and when your picks get old.
- If you follow a lot of shows, New Episodes no longer misses new episodes from some of them, and pulling down on New Episodes checks all your shows (up to 200), not just a few.
- The Brief opens as soon as it's ready, even when its stories are slow to load. They appear when they arrive.
- New episode notifications from different shows no longer replace each other or open the wrong show. Each show gets its own notification, grouped together, for up to 16 shows at a time.
- On a show's page, Mark all as played and Mark all as unplayed now change every episode, not just the newest 200. If the episodes can't load, nothing changes and boxlore tells you.
- When boxlore can't read a long show's own feed, searching that show now looks through up to 11,000 of its episodes instead of the newest 1,000, and says so when there are more. If the search can't run, the page says so and lets you try again instead of showing no matches.
- On a show page, episodes that don't load are tried again and then show Try again, instead of the list just ending. Select all on a long show selects every episode or says it couldn't.
- After you sign in on a new phone, every show you follow now gets its name and artwork. Some used to stay as “Loading...” with no artwork.
- Importing a large library from another podcast app no longer stops partway. Shows boxlore can't add right away are added later while boxlore is open.
