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

- The Marquee style on Home now scrolls with the page in one go, and its cover stays clear behind the words.
- Home keeps your place in Your shows, the Brief and its menus when a foldable opens or closes or a tablet window changes size.
- Home's menus, the show picker under Because you like, the genre picker and the rating sheets now match the rest of boxlore.
- The Daily's show page now opens under the dawn sky from its cover, with the sun peeking over the horizon, and its latest episode shows its whole picture beside the episode description.
- In Android Auto, a show's page now lists its episodes from newest to oldest. Episodes you've started stay in their place and show the time left.
- Episodes you archive on one phone are now archived on your other signed-in phones and tablets too.
- On a half-open foldable, pages now use the whole screen, and the sleep timer prompt and playback notices stay clear of the fold.
- On a phone turned sideways, more show posters fit in each row, and the bottom bar and mini player stay clear of the navigation buttons and camera.

## Fixed

- On the Galaxy Z Fold cover screen and in split-screen windows, Home's top cards now show one full card and a peek of the next, instead of squeezed cards with broken titles.
- On a phone turned sideways, Home's buttons and cards stay clear of the navigation buttons and camera, and Home keeps your place when you turn the phone.
- The message that you can't pin another show now appears above the bottom bar instead of behind it.
- Importing an OPML file that lists a show you already follow no longer turns off that show's new-episode notifications and auto-download.
- Importing an OPML file that lists a show twice now adds every show in the file in one go.
- When you use boxlore on more than one device, one that was offline or signed out no longer sets your progress or likes back to an older point, and devices that showed different progress now agree.
- Shows you unfollow on another device or on your watch are now cleared from this phone's saved shows, just as when you unfollow them here.
- Casting to a TV or speaker no longer leaves your phone on "Connecting…" once the episode is playing there. The device list now shows which one you're casting to.
- Reopening boxlore while it's casting no longer pauses the episode on your TV, jumps it back or resets its speed.
- An episode boxlore picked up where you left off now shows its cover when you cast it to a TV, instead of a placeholder.
- Turning your phone sideways and back, unfolding it or resizing boxlore's window no longer opens the full player by itself.
- Sheets and dialogs now scroll on short screens, like the Galaxy Z Fold cover screen and phones turned sideways, so their last button stays in reach.
- Sheets keep what you picked, typed or scrolled to when a tablet or foldable changes shape.
- Long button, tab and side navigation names in other languages or at large text sizes now fit instead of being cut off.
