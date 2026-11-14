# `:core:wearlink`

## Purpose

Owns the link between the phone app and boxlore on a Wear OS watch: the values the two exchange, how they are packed, the connection over the system's wearable service, and the phone's end of the conversation.

The watch is treated as one more device. Follows, unfollows and listening travel as the same sync rows the phone exchanges with the cloud (`:core:network`), so the phone merges them with the rules it already has.

It does not own the watch's end (that is [`:wear`](../../wear/README.md)), the phone's library or player (the app passes them in as ports), or any screen.

## Public API

- `WearLinkProtocol`: the paths and capability names. They are stored by the system on both devices and never change.
- Values (`WearLinkModels.kt`): `PhoneLibrary`, `WatchChanges`, `PhoneNowPlaying`, `WatchNowPlaying`, `WearLinkSettings`, `WearLinkCommand`, `EpisodeListRequest` / `EpisodeListReply`, `WearLinkShow`, `WearLinkEpisode`.
- `WearLinkCodec`: compressed JSON. Unknown fields are ignored and unreadable bytes decode to null; `encodeLibrary` cuts a library that would not fit one item.
- `WearLinkTransport`: publish, read, follow changes, send and request. `GmsWearLinkTransport` is the real one; every failure answers "not available". `NoWearLinkTransport` and `RecordingWearLinkTransport` are for devices without the service and for tests.
- `PhoneWearLink`: the phone's end, one per app. `start()` does nothing until a watch has made contact once. `onItem`, `onCommand` and `onRequest` take what the app's listener service receives. `status` and `settings` feed the Wear OS settings page; `setShowsLayout` and `setStorageLimitMb` change the watch's settings. `playOnWatch` moves the phone's episode, place and queue to the watch; `playOnWatchOffer` is true after the phone took over from a playing watch, until `dismissPlayOnWatchOffer`.
- `PhoneLibraryPort`, `PhonePlayerPort`: what the app implements over its repositories.
- `PhoneLinkStore` (`SharedPreferencesPhoneLinkStore`, `PhoneLinkStore.InMemory`): what the phone remembers about the link.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/wearlink/
  WearLinkModels.kt         # protocol names and exchanged values
  WearLinkCodec.kt          # packing and the one-item size rule
  WearLinkTransport.kt      # the connection port, the absent link, the recording link
  GmsWearLinkTransport.kt   # the system's wearable service
  PhoneLinkPorts.kt         # ports the app implements, link status, the phone's store
  PhoneWearLink.kt          # the phone's end and its rules
```

## Dependencies

- `api`: [`:core:network`](../network/README.md) (the sync rows).
- Libraries: play-services-wearable, kotlinx-serialization-json, coroutines (with play-services), core-ktx.
- Used by `:app` and `:wear`.

## Threading

`PhoneWearLink` runs on the scope it is given and never blocks a caller. The ports decide their own threads.

## Stable names

- Paths under `/boxlore/phone`, `/boxlore/watch`, `/boxlore/command`, `/boxlore/watch-command`, `/boxlore/episodes`, and the capabilities `boxlore_phone` and `boxlore_watch`.
- Preferences file `boxlore_wear_link` and its keys (`SharedPreferencesPhoneLinkStore`).

## Tests

| Suite | Covers |
| :--- | :--- |
| `WearLinkCodecTest` | Every value survives packing, unreadable bytes, newer fields, the size rule, the phone's moving position |
| `PhoneWearLinkTest` | Idle until a watch appears, merging and confirming watch changes, a watch that starts over, player reports with the queue, commands, taking over from a playing watch, handing playback to the watch, episode requests, settings |
