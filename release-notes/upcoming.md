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

## Fixed

- On show pages, Select all selects every episode of the show instead of stopping at 100, and while you search it selects your search results. A selected episode on a show's own-style page now stays readable.
- Your monthly story with Echo opens again from Listening history instead of closing the app. Echo now lands smoothly between cards, the story fills the status and navigation bars, and its card's title no longer runs under the show covers.
- Playback controls on the lock screen, in notifications and in Samsung's Now Bar show rewind, play and forward in that order, even when nothing is queued next, with a Next episode button beside them when there is.
