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

- When Google Play can’t confirm your phone, boxlore now runs Cloudflare’s quick check as it opens, not only during setup, so sync, feedback and recommendations keep working. Most of the time you won’t see it.
- boxlore on Android TV now starts with signing in: enter the code it shows in boxlore on your phone, under Settings › Cloud Sync & Backups. After about half an hour without a sign-in, choose Show a new code.
- boxlore on Wear OS now signs in through your phone before it shows your library: open boxlore on your phone and the watch signs in by itself. Episodes already downloaded keep playing meanwhile.

## Fixed

- On episode pages, Mark played stays at the end of the date and length line again, and long details scroll beside it instead of pushing it onto a line of its own.
