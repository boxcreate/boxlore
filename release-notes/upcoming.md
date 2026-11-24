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

- boxlore now has an Android TV app: browse charts and your shows, pick up where you left off, and keep listening as one episode leads to the next.

## New

- To bring your shows to your TV, open Cloud Sync & Backups on your phone, choose Sign in on a TV, and enter the code your TV shows.

## Improved

- The Marquee style on Home now scrolls with the page in one go, and its cover stays clear behind the words.
- The Daily's show page now opens under the dawn sky from its cover, with the sun peeking over the horizon, and its latest episode shows its whole picture beside the episode description.
- In Android Auto, a show's page now lists its episodes from newest to oldest. Episodes you've started stay in their place and show the time left.
- Episodes you archive on one phone are now archived on your other signed-in phones and tablets too.
- On a half-open foldable, pages now use the whole screen, and the sleep timer prompt and playback notices stay clear of the fold. Half-open on a table, the full-screen transcript sits above the fold with its controls below.
- On a phone turned sideways, more show posters fit in each row, and the bottom bar and mini player stay clear of the navigation buttons and camera.
- The player fits more screens: on a phone turned sideways the Standard player style shows the cover beside the controls, an unfolded foldable shows Up next beside the player, and on tablets Up next fills the space under the controls.
- Getting started with boxlore fits more screens: Echo's questions and show cards always show in full, the welcome screen fits the Galaxy Z Fold cover, and it sits beside its picture on sideways phones and tablets.
- Picking your language now flows straight into the welcome screen.
- While you get started, search keeps more shows in view on sideways phones, and Shows for you lays its shows out in rows on tablets.

## Fixed

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
- After a video plays full screen, your phone turns with you again instead of staying upright.
- The player keeps an open speed or sleep panel when you fold, unfold or turn your phone, and its place on the page when you turn it. A running sleep timer stays highlighted when you open the sleep options again, and an episode you remove from Up next stays removed even if you fold or unfold before Undo goes away.
- Pick topics instead now keeps the topics you already chose with Echo.
- If you skip the quick check before similar shows, boxlore now says so instead of asking you to check your connection.
