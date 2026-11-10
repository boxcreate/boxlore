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

## New

## Improved

- On Home, a show you listen to oldest first now picks up exactly where you left off, even deep into a long series, and skips trailers. Daily mix suggests the same next episode.
- Android Auto has a new layout. Home shows what you're midway through, then large blocks for New episodes, Your shows, Daily mix, Downloads and more, with Continue, New and Shows one tap away as tabs.
- In Android Auto, tapping an episode in New episodes, Daily mix, Downloads, Liked or Drive picks keeps playing down that list, and an episode you resume is followed by your queue.
- Android Auto lists now update when you queue, download, like or follow something on your phone.

## Fixed

- On Home, a show you've heard every episode of now lists its latest episodes instead of an empty list, and a show whose episodes can't load lets you try again.
- In Android Auto, artwork loads faster, and every episode has a picture: one with no image of its own shows its show's cover.
- In Android Auto, tapping something that can't be played no longer stops what you're listening to.
