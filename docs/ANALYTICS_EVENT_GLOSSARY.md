# Analytics event glossary

Canonical client analytics contract. Emit only listed `event_name` values.
Companion CSV: [`docs/analytics/event_glossary.csv`](analytics/event_glossary.csv).
Coverage inventory: [`docs/analytics/glossary_emission_coverage.csv`](analytics/glossary_emission_coverage.csv).
Architecture: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

The CSV gives every event a `level` and a `monthly_per_install` figure:
- **`level`:** `always` (every install sends it), `sampled` (a fixed 10% of installs send it, tagged `sample_percent`), or `off` (nobody sends it until the `analytics-policy-v1` flag turns it on). It must match `AnalyticsPolicy.DEFAULT_LEVELS`; SDK-backed rows use their PostHog event.
- **`monthly_per_install`:** events per active install over 30 days, from PostHog, with test phones left out. New events need an estimate, and `AnalyticsBudgetTest` keeps the total within the monthly budget.

## SDK-backed vs custom (no double-count)

| Glossary name | Production source of truth | App action |
| :--- | :--- | :--- |
| `app_open` | SDK **Application Opened** | Do **not** `capture("app_open")`. Keep `captureApplicationLifecycleEvents = true`. |
| `app_background` | SDK **Application Backgrounded** | Do **not** emit custom background. |
| Install volume (DNI/WNI/MNI / Weekly Pulse) | SDK **Application Installed** | Keep Weekly Pulse on Application Installed / Application Opened. |
| `install_attributed` | Person `$set_once` `install_channel` | Attribution only — **never** a second install-count event. |
| `deep_link_opened` | Custom glossary emit | SDK `captureDeepLinks = false`. |

Launch enrichment (`first_seen_at`, `onboarding_status=pending`) goes on **person properties**, not a second open event.

## Enums

### entry_point

`home_hero_resume`, `home_hero_jump_back_in`, `home_hero_new_episodes`, `home_hero_spotlight`, `home_mixtape`, `home_adaptive_*`, `home_because_you_like`, `home_discover_grid`, `home_video_spotlight`, `home_recommendations`, `briefing`, `explore_for_you`, `explore_trending`, `explore_category`, `explore_search_shows`, `explore_search_episodes`, `learn`, `podcast_detail`, `episode_detail`, `queue`, `queue_auto_advance`, `queue_skip`, `library_latest_episodes`, `downloads`, `history`, `liked`, `notification`, `deep_link`, `share`, `install_referrer`, `android_auto`, `android_auto_continue`, `android_auto_queue`, `android_auto_new_episodes`, `android_auto_mixtape`, `android_auto_downloads`, `android_auto_liked`, `android_auto_history`, `android_auto_discover`, `android_auto_voice`, `android_auto_play_all`, `mini_player`, `session_restore`, `player_up_next`, `smart_queue`, `onboarding_suggestion`, `unknown`

Aliases (normalized in app; do not add as new enum values): `learn_history` → `learn`. Brief audio play tags `briefing` on `playback_*` (interactions remain on `daily_briefing_action`).

### surface

`home`, `explore`, `learn`, `library_hub`, `library_subscriptions`, `library_liked`, `library_downloads`, `library_history`, `podcast_detail`, `episode_detail`, `player_full`, `player_mini`, `queue_sheet`, `settings`, `onboarding`, `briefing`, `android_auto`, `system_notification`, `share_sheet`

### Listen record

`playback_session_ended` and `playback_interrupted` values (`PlaybackListenValues`):
- **`end_reason`:** `paused` (including Android pausing for a call or unplugged headphones; `pause_reason` says which), `finished`, `switched_episode`, `stopped`, `app_closed`.
- **`controlled_by`:** `app`, `car` (Android Auto or Android Automotive), `notification` (the media notification and system media controls), `other` (headset buttons, a watch, the Assistant). A media button keeps its real sender even though the player receives it from the notification. A listen that follows another keeps its controller. `client_surface` is `car` only when the car controls the listen.
- **`output`:** where the sound went when the listen ended: `speaker`, `wired`, `bluetooth` (hearing aids included), `cast`, `other`. The car is a controller, not an output.
- **`playback_mode`:** `download` when any stored ID of the episode holds a finished download and it plays on this phone, `stream` otherwise (Cast always streams), `unknown` when the listen ended before the lookup answered.
- **`heard_seconds`** comes from the player's position (natural advances only, so seeks and paused time never count), including the audio between the last once-a-second sample and a seek or the end; **`elapsed_seconds`** is the time the listen took. `qualified` is `heard_seconds` of 60 or more. **`start_wait_ms`** counts from the first play request of a press, so resolving a resumed session counts too.
- **`screen` / `previous_screen`** on `playback_started` and `playback_session_ended`: the screen the listener asked to play from and the one before it ([Screens](#screens)); left out for plays from the car, voice and other controllers.
- **Errors:** `error_message` and everything sent to error tracking have links and signed tokens removed (`AnalyticsRedaction`); error tracking gets a copy of each error that names the original type and keeps its stack.

### Screens

`screen` and `previous_screen` (`screen_viewed`, `screen_left`, plays): snake_case screen names from `TrackScreenView`. Screens with a view event of their own (`podcast_detail_viewed`, `episode_detail_viewed`, `explore_screen_viewed`, `library_destination_viewed`, `learn_screen_viewed`, `support_page_viewed`, `onboarding_step_viewed`, `settings_interaction` views) keep it and send nothing else; their leaving summaries are `screen_left`, under the same screen names. On a two-pane tablet layout, `screen` on a play is the pane opened most recently.

### Home hero look

`hero_card_style` / `home_hero_card_style`: `block`, `cover`, `tide`, `duotone`, `halo`, `marquee` (the names in Settings; stored keys `standard` and `classic` are reported as `block` and `cover`). `hero_colors` / `home_hero_colors`: `dark`, `artwork`, `theme` (App theme, the accent color), or `none` for Block, which takes no artwork colors. Marquee's **Full color** is the stored Dark choice, so it reports `dark`. `changed`: `style` or `colors`. `home_hero_style_selected` is skipped when a pick leaves the look unchanged. Tablets show Marquee as Cover; the event still reports the picked style.

### LeaveMeAlone sections

`section` on `recommendation_section_toggled`: `brief` (The Brief), `picked_for_you` (Home's Picked for you and Explore's For you tab), `because_you_like`, `time_of_day_picks` (the greeting and its curated rows) and `more_like_this` (Try these episodes on episode pages). `source`: `settings` (Settings › Library › LeaveMeAlone) or `brief_card` (the Brief card's Always hide, which is the same choice as turning The Brief off). `sections_off`: how many of the five are off after the change. A section that is off is hidden and makes no requests.

### Episode identity

`episode_id` is always the episode's ID on this phone. Events that carry it may also carry `recipe_id`, boxlore's own ID for the same episode (a decimal string), when the app knows it. `home_surface_impression` and `explore_surface_impression` may carry `recipe_ids` for episode surfaces (the known recipe IDs of `episode_ids`, in surface order). Join impressions, taps and plays across devices on `recipe_id`; it is absent for episodes without one (shows added by feed address, and episodes not yet matched). The recipe ID is the episode ID itself when that is a recipe-range ID, else the episode's own `recipeId` or the recipe ID of its trusted alias; it is never guessed. Events are never held back for it: an episode whose recipe ID is not in memory yet is looked up in the background, so the first event of a stored older ID can lack it while the tap and play after the impression carry it.

### Feed reads and addresses

`feed_fetch_result` fires once per show-page read (`context=show_page`, with `show_id`) and once per background pass and source as a summary (`context=background_pass`, with `shows_count`, `ok_count`, `not_modified_count` and `failed_count`); a pass that read no feeds sends nothing. `source`: `feed` (the publisher address), `transport` (boxlore's copy of the same feed), `v2_episodes` (the server's episode list, used when the feed can't be read) or `cache` (the copy on the phone). `result` on a show page: `ok`, `not_modified`, `network_error`, `timeout`, `http_error` (with `http_status`), `gone` (404 or 410, with `http_status`) or `parse_error`; `http_status` is never sent with any other result; on a background pass: `ok` (nothing failed), `mixed` or `failed` (every read failed). `completeness` (`partial` for the newest items, `complete` for the whole feed) is sent only for successful show-page reads.

`feed_url_corrected` fires once per saved-address swap of a followed show. `reason`: `dead` (the saved address stopped answering), `moved` (a permanent redirect or `itunes:new-feed-url`) or `registry` (the server's show registry holds a different confirmed address). `step`: `upgrade_repair`, `daily_check`, `feed_refresh` or `feed_report`.

Neither event carries an address. `show_id` is the Podcast Index ID or the hashed `rss:` ID; a value that looks like an address is left out (and `feed_url_corrected` is then not sent).

`own_ids_upgrade_completed` fires once per install, when the one-time repair after updating from an older build finishes: every followed Podcast Index show the update queued has been checked, swapped where its address moved, read again and registered for notifications again. It never fires on a fresh install, and a restored phone that brings back the record does not send it again. `outcome`: `done`, or `gave_up` when at least one show reached its retry cap. `shows_count` (shows the update queued), `shows_gave_up_count`, `addresses_swapped_count`, `notifications_reregistered_count`, `runs_count` (repair runs, the last included) and `elapsed_hours` (from the first run to the last, rounded down to a tenth). Counts only: no show IDs, titles or addresses.

### content_type

`podcast`, `episode`, `briefing`, `mixtape`, `announcement`, `listening_story` (the monthly story's share card), `unknown`

### Other

- playback_mode: `stream` | `download` | `unknown` (see Listen record)
- client_surface: `phone` | `car` (`car` only when the car controls the listen)
- install_channel: `organic` | `share` | `utm` | `unknown` (Play's `(not set)` referrer is `unknown`); set once on the person
- first_onboarding_method / first_user_intent / initial_podcasts_subscribed: set once at the first setup; `onboarding_method` and `user_intent` follow the latest route
- search_mode: `show_keyword` | `episode_semantic`
- mix_mode: `daily` | `offline`
- pause_reason: `user_voluntary` | `interruption` | `error` | `sleep_timer` | `app_kill` | `auto_pause`
- subscription_count_bucket: `0` | `1_3` | `4_10` | `10_plus`
- daypart: `morning` | `afternoon` | `evening` | `late_night`

## Events

| event_name | meaning | required_properties | optional_properties | pii_risk |
| :--- | :--- | :--- | :--- | :--- |
| `app_open` | DAU volume via SDK **Application Opened** (glossary alias; not custom-emitted) | — (SDK) | person props for enrichment | none |
| `app_background` | Session end via SDK **Application Backgrounded** (not custom-emitted) | — (SDK) | — | none |
| `install_attributed` | Channel attribution only (`$set_once` install_channel); volume is **Application Installed** | install_channel on person | referrer_raw; utm_source; share_token | none |
| `deep_link_opened` | Inbound deep/share link handled | link_scheme:string; is_first_open:bool; cold_start:bool | link_host:string; content_type:enum; podcast_id:string; episode_id:string; recipe_id:string | none |
| `onboarding_started` | User entered onboarding | entry_point:string | is_reentry:bool | none |
| `onboarding_flow_selected` | User chose AI/genre/search/import | flow_type:string; entry_point:string | — | none |
| `onboarding_step_viewed` | A discrete onboarding step became visible | step_name:string; flow_type:string | step_index:int | none |
| `onboarding_abandoned` | User left before complete | last_step:string; flow_type:string | time_spent_seconds:int; subscribed_count:int | none |
| `onboarding_completed` | User finished an onboarding path | method:string; total_subscribed_count:int | screen:string; subscribed_podcasts_list:list; total_onboarding_time_seconds:float; entry_point:string; favorite_genres:list; did_scroll_suggestions:bool; did_switch_from_ai:bool; import_type:string; searches_performed:int | none |
| `onboarding_similar_choice` | After search or import, the listener chose See similar shows (`similar`), Go to Home (`home`) or Skip to Home while suggestions loaded (`skip_loading`) | path:string; choice:string; seed_count:int | — | none |
| `onboarding_ai_turn_submitted` | User sent a message in onboarding AI chat (user_input_text capped at 500 characters) | turn_number:int; user_input_text:string; has_custom_input:bool | selected_options:list; time_spent_seconds:float | logged_by_policy |
| `onboarding_ai_response_received` | Assistant reply returned in onboarding AI | turn_number:int; options_count:int | options_list:list; duration_seconds:float; detected_intent:string | none |
| `onboarding_ai_search_redirect` | AI suggested leaving chat for search | turn_number:int | suggested_query:string | logged_by_policy |
| `onboarding_ai_synthesis_completed` | AI produced podcast suggestions | rows_count:int; podcasts_count:int | duration_seconds:float | none |
| `onboarding_ai_synthesis_failed` | AI suggestion generation failed | — | error_message:string | none |
| `onboarding_search_performed` | Search during onboarding | search_query:string; results_count:int | query_length:int | logged_by_policy |
| `onboarding_search_podcast_subscribed` | Subscribed from onboarding search result | podcast_id:string; total_subscribed_count:int | podcast_name:string | none |
| `onboarding_import_sheet_opened` | OPML/import sheet shown | — | entry_point:string | none |
| `onboarding_import_failed` | OPML/library import failed | import_type:string | error_message:string | none |
| `onboarding_manual_step_completed` | Genre/manual onboarding step finished | step_name:string; selections_count:int | selections_list:list; time_spent_seconds:float | none |
| `playback_started` | Audio playback started (`is_resume` means finished before; `previously_finished` says so by name) | episode_id:string; podcast_id:string; entry_point:enum; is_resume:bool; playback_mode:enum; client_surface:enum; speed:float; is_subscribed:bool | recipe_id:string; position_seconds:float; duration_seconds:float; listen_id:string; previously_finished:bool; controlled_by:enum; screen:string; previous_screen:string | none |
| `playback_heartbeat` | Periodic progress while playing | episode_id:string; podcast_id:string; entry_point:enum; position_seconds:float; playback_mode:enum; client_surface:enum | recipe_id:string; duration_seconds:float; percent_complete:float; milestone:int; speed:float; is_subscribed:bool | none |
| `playback_paused` | Playback paused (off by default; replaced by `playback_session_ended`) | episode_id:string; podcast_id:string; entry_point:enum; position_seconds:float; listened_delta_seconds:float; pause_reason:enum; playback_mode:enum; client_surface:enum | recipe_id:string; duration_seconds:float; percent_complete:float; is_subscribed:bool | none |
| `playback_completed` | Episode reached completion | episode_id:string; podcast_id:string; entry_point:enum; listened_delta_seconds:float; playback_mode:enum; client_surface:enum | recipe_id:string; duration_seconds:float; is_subscribed:bool; speed:float | none |
| `playback_error` | Playback failed, once per cause per episode until a listen starts; also sent to error tracking | error_type:string; entry_point:enum | episode_id:string; recipe_id:string; podcast_id:string; error_message:string; playback_mode:enum; client_surface:enum; listen_id:string | none |
| `playback_buffering` | Buffering stall of 5 seconds or more | entry_point:enum | episode_id:string; recipe_id:string; podcast_id:string; buffer_duration_ms:int; playback_mode:enum; client_surface:enum | none |
| `playback_seeked` | User seeked in episode | episode_id:string; podcast_id:string; entry_point:enum; from_seconds:float; to_seconds:float | recipe_id:string; seek_source:string; client_surface:enum | none |
| `playback_session_ended` | One record per listen, from sound starting until it pauses, finishes, switches episode, stops or the app closes; `qualified` from 60 s heard | listen_id:string; episode_id:string; entry_point:enum; end_reason:enum; heard_seconds:float; elapsed_seconds:float; qualified:bool; completed:bool; previously_finished:bool; playback_mode:enum; client_surface:enum; controlled_by:enum; output:enum; speed:float; seeks_forward:int; seeks_back:int; skipped_forward_seconds:float; skipped_back_seconds:float; buffering_count:int; buffering_seconds:float; start_position_seconds:float; end_position_seconds:float; duration_seconds:float | recipe_id:string; podcast_id:string; pause_reason:enum; start_wait_ms:int; queue_size:int; screen:string; previous_screen:string | none |
| `playback_interrupted` | A listen the last run left unfinished because Android ended the app, sent at the next launch | listen_id:string; episode_id:string; entry_point:enum; heard_seconds:float; elapsed_seconds:float; in_background:bool; playback_mode:enum; controlled_by:enum; client_surface:enum | recipe_id:string; podcast_id:string | none |
| `screen_viewed` | A screen, tab, sheet or dialog without a view event of its own was shown, once per visit (`TrackScreenView`; rotating, folding, resizing or coming back from a screen on top doesn't send it again) | screen:string | previous_screen:string; tab:string | none |
| `screen_left` | A screen's leaving summary (time on it, what was done there), sampled; it used to go out under the screen's view event, which counted each visit twice | screen:string; time_spent_seconds:float | episode_id:string; recipe_id:string; podcast_id:string; destination:string | none |
| `session_restore_prompt` | Resume previous session prompt shown/acted | action:string | episode_id:string; recipe_id:string; podcast_id:string; position_seconds:float | none |
| `podcast_subscription_toggled` | User subscribed or unsubscribed | podcast_id:string; is_subscribed:bool | surface:string; source:string | none |
| `notification_permission_requested` | System permission prompt requested | — | surface:string | none |
| `notification_permission_decided` | User granted/denied notifications | granted:bool | surface:string | none |
| `notification_tapped` | User opened a push/local notification | notification_type:string | podcast_id:string; episode_id:string; recipe_id:string; target_route:string | none |
| `notification_received` | Push/local notification delivered | notification_type:string | podcast_id:string; episode_id:string; recipe_id:string | none |
| `identity_reset` | Analytics identity cleared/reset | — | reason:string | none |
| `app_check_status` | App Check token attempt result, once per API session exchange that asks App Check (App Check is no longer fetched per launch or per request) | token_obtained:bool; provider:string | reason:string (`play_integrity_<code>`, `http_<status>`, `too_many_attempts`, `network`, `other`) | none |
| `api_session_status` | API session result: a `/auth/session` exchange (`kind=exchange`), the first silent renewal of a token or a renewal problem (`kind=renew`), or a Turnstile step-up (`kind=step_up`). Categories only, never tokens or ids | kind:enum; success:bool | trigger:enum (`launch`, `expired`, `rejected`, `refresh`, `upgrade`, `step_up`, `retry`); attestation:enum (`appcheck`, `challenge`, `none`); limited:bool; duration_ms:int; app_check_attempted:bool; renew_outcome:enum (`renewed`, `proof_unavailable`, `rejected`); step_up_outcome:enum (`passed`, `challenge_failed`, `not_accepted`); failure_reason:string; server_reason:string | none |
| `first_episode_played` | First-ever play milestone | — | episode_id:string; recipe_id:string; podcast_id:string; entry_point:enum; hours_since_install:float | none |
| `home_surface_tapped` | First-class Home component tap | surface_component:string | rail_intent:string; content_id:string; position_index:int; items_count:int; mix_mode:string; podcast_id:string; podcast_name:string; episode_id:string; recipe_id:string; episode_title:string; click_target:string | none |
| `home_surface_impression` | Home rail/block became visible | surface_component:string | items_count:int; podcast_ids:list; recipe_ids:list | none |
| `search_performed` | User ran show or episode search | surface:string; search_mode:enum; search_query:string; results_count:int | result_quality:enum; query_length:int | logged_by_policy |
| `search_result_tapped` | User tapped a search result | surface:string; result_type:enum | search_mode:enum; podcast_id:string; episode_id:string; recipe_id:string; position_index:int; search_query:string | logged_by_policy |
| `learn_card_action` | User acted on a Learn card | action:string; episode_id:string; podcast_id:string | recipe_id:string; position_index:int | none |
| `learn_screen_viewed` | Learn tab/screen opened | — | cards_available:int | none |
| `explore_screen_viewed` | Explore opened | — | tab:string | none |
| `explore_recommendation_tapped` | For You/trending card tapped (taps only) | — | podcast_id:string; episode_id:string; recipe_id:string; position_index:int; rail:string | none |
| `explore_surface_impression` | Explore rail became visible | surface_component:string | items_count:int; episode_ids:list; recipe_ids:list | none |
| `queue_modified` | Queue add/remove/reorder/clear (clear only when the listener chose it) | action:string | episode_id:string; recipe_id:string; podcast_id:string; queue_size:int; source:string; items_count:int | none |
| `episode_liked_toggled` | Like/unlike episode | episode_id:string; podcast_id:string; is_liked:bool | recipe_id:string; surface:string | none |
| `episode_mark_played` | User marked episode played/unplayed | episode_id:string; podcast_id:string; is_played:bool | recipe_id:string; surface:string | none |
| `download_requested` | User or Smart Downloads requested download | episode_id:string; podcast_id:string; source:string | recipe_id:string; wifi_only:bool | none |
| `download_completed` | Episode finished downloading | episode_id:string; podcast_id:string | recipe_id:string; source:string; bytes:long; duration_ms:long | none |
| `download_failed` | Download failed | error_type:string | episode_id:string; recipe_id:string; podcast_id:string; source:string; error_message:string | none |
| `smart_download_sync` | Smart Downloads sync ran | — | requested_count:int; completed_count:int; failed_count:int; cleaned_count:int; trigger:string | none |
| `show_notification_toggled` | Per-show notification preference changed | podcast_id:string; enabled:bool | — | none |
| `share_content` | User shared podcast/episode/app | content_type:enum | podcast_id:string; episode_id:string; recipe_id:string; channel:string; surface:string | none |
| `backup_restore_result` | Library backup or restore finished | action:string; success:bool | item_count:int; format:string; error_message:string | none |
| `feedback_submitted` | NPS survey trigger (eligible or manual); the survey shows on any of these | feedback_type:string | score:int; source:string | none |
| `engagement_prompt_shown` | A Home review prompt was shown | prompt_type:string; source:string | completed_episodes:int; nps_score:int | none |
| `store_review_opened` | Play review sheet or store page opened from boxlore | method:string; source:string | — | none |
| `feedback_sent` | In-app feedback form accepted by the server | category:string; with_email:bool; with_diagnostics:bool | — | none |
| `library_destination_viewed` | Library hub or sub-destination opened | destination:string | item_count:int | none |
| `podcast_detail_viewed` | Podcast info screen opened | podcast_id:string | is_subscribed:bool | none |
| `episode_detail_viewed` | Episode info screen opened | episode_id:string; podcast_id:string | recipe_id:string | none |
| `nav_tab_clicked` | Bottom nav tab selected | tab:string | previous_tab:string | none |
| `settings_interaction` | Settings screen view or control; a control reports only when its value changes (`smart_downloads_*`, `auto_download_*`, `mini_player_seek_buttons_toggled`, `full_player_style_changed` among the keys) | action:string | setting_key:string; value:string | none |
| `custom_theme_editor_opened` | Custom theme editor becomes visible | entry_point:string | — | none |
| `custom_theme_saved` | Custom theme preferences saved successfully | background_style:string | — | none |
| `home_hero_style_selected` | Home hero card style or its colors picked in Settings › Appearance › Home | hero_card_style:string; hero_colors:string; hero_style_combo:string; changed:string | previous_hero_style_combo:string | none |
| `recommendation_section_toggled` | A LeaveMeAlone switch flipped, or the Brief hidden from its card | section:string; enabled:bool; source:string; sections_off:int | — | none |
| `feature_announcement_action` | In-app/feature announcement viewed/dismissed/acted | action:string | feature_id:string; category:string; has_image:bool; has_action:bool; action_label:string; announcement_id:string; announcement_title:string; presentation:string | none |
| `offline_mode_entered` | App detected offline / offline UI | — | reason:string | none |
| `player_chrome_interaction` | Mini/full player, control bar, or playback-route action | surface:string; action:string | screen:string; volume_control_available:bool | none |
| `daily_briefing_action` | Briefing impression or interaction | action:string; region:string; date:string | content_id:string; source:string; chapter_index:int; chapter_title:string; method:string; playback_status:string; previous_region:string; episode_id:string; recipe_id:string; episode_title:string; podcast_id:string; podcast_title:string | none |
| `home_import_banner_action` | Import banner shown/clicked/dismissed | action:string | — | none |
| `android_auto_connected` | Auto session began in a car (car mode or Android Automotive; Android Auto's background connections don't count) | — | session_id:string | none |
| `android_auto_disconnected` | Auto session ended | — | session_id:string; duration_seconds:int | none |
| `android_auto_browse` | User browsed Auto media tree | node:string | action:string | none |
| `adaptive_ranking_status` | Ranking engine status/update | status:string | details:string | none |
| `learn_caught_up` | User reached Learn caught-up state | — | cards_remaining:int | none |
| `catalog_miss` | Catalog/lookup miss | lookup_type:string | key:string | none |
| `rss_refresh_failed` | RSS feed refresh failed | — | podcast_id:string; error_type:string | none |
| `progress_sync_anomaly` | Progress sync inconsistency detected | anomaly_type:string | episode_id:string; recipe_id:string | none |
| `late_night_safeguard_decision` | Late-night listening safeguard fired | decision:string | — | none |
| `proxy_fallback_triggered` | Image load fell back to proxy | — | reason:string | none |
| `feed_fetch_result` | A show page read its episodes, or a background pass read followed feeds | context:enum; source:enum; result:enum | show_id:string; completeness:enum; http_status:int; shows_count:int; ok_count:int; not_modified_count:int; failed_count:int | none |
| `feed_url_corrected` | A followed show's saved feed address was replaced (one event per swap) | show_id:string; reason:enum; step:enum | — | none |
| `own_ids_upgrade_completed` | The one-time repair after updating from an older build finished (once per install) | outcome:enum; shows_count:int; shows_gave_up_count:int; addresses_swapped_count:int; notifications_reregistered_count:int; runs_count:int; elapsed_hours:float | — | none |
| `support_page_viewed` | Support boxlore development screen opened (one per visit; the `support-world` experiment exposure) | — | support_world:enum; assignment_source:enum; entry_point:string; local_hour:int; weekday:int | none |
| `support_tier_toggled` | Support tier artifact swiped, picked or lore inspected | tier_title:string; codename:string; amount:string | is_lore_inspect:bool; support_world:enum; assignment_source:enum; tier_index:int | none |
| `support_donate_clicked` | User tapped donate CTA button for a support tier | tier_title:string; codename:string; amount:string | support_world:enum; assignment_source:enum; tier_index:int; amount_usd:float; payments_live:bool | none |
| `support_entry_viewed` | Support boxlore entry shown in Settings: phone top-bar button or tablet category list (once per hub or list view; not on rotation) | surface:enum | — | none |
| `support_page_left` | Support boxlore page closed after a visit (never on rotation or resize) | support_world:enum; assignment_source:enum; dwell_ms:int; tiers_viewed:int; pay_tapped:bool | — | none |

`surface` is `settings_top_bar` or `settings_category_list`. `support_world` is `control`, `neon`, `orbit`, `alchemy`, `abyss` or `sky`. `assignment_source` is `experiment` (counted), `fallback` (flag not loaded or unknown, shows control) or `debug_override` (debug builds only). Read results from `experiment` rows only.

## Person properties

| property | set_when | notes |
| :--- | :--- | :--- |
| first_seen_at | first process | `$set_once` |
| onboarding_status | pending / completed | `pending` on first launch, `completed` when onboarding finishes |
| onboarding_method | ai_chat / manual_genre / search / import / skip | set with `onboarding_status=completed` |
| user_intent | onboarding complete | `casual_browser` (skip) / `ai_guided_listener` (AI chat) / `targeted_listener` (search) / `selective_curator` (manual genres) / `migrating_power_user` (import) |
| favorite_genres | onboarding complete (AI chat, manual genres) | genre list |
| listener_profile / genre_breadth / genre_enthusiasm | onboarding complete (manual genres) | derived from the picked genres (`GenrePersonaLogic`) |
| install_channel | first open | |
| referrer_raw / utm_source / share_token | first open, with the install referrer | `$set_once`, only when present |
| notifications_enabled | permission decide | |
| initial_podcasts_subscribed | onboarding complete | |
| first_play_at | first episode played | `$set_once`, with `first_episode_played_logged=true` |
| home_hero_card_style | launch + hero style change | `block` / `cover` / `tide` / `duotone` / `halo` / `marquee`; set for the default too, once per process |
| home_hero_colors | launch + hero colors change | `dark` / `artwork` / `theme`; `none` for Block |
| home_hero_style_combo | launch + any hero look change | style and colors in one value (`cover_artwork`, `block`); resent after an identity reset |

## Logging policy

- Log `user_input_text` on AI chat turns and `search_query` on search events (`pii_risk=logged_by_policy`).
- Before emission, scrub those fields: remove email addresses, password-like tokens, and any `http(s)` audio/enclosure/media URLs. Keep the remaining free-text query/message.
- Never emit emails, passwords, or audio/enclosure URLs as standalone property values.
- `entry_point` is required on all `playback_*` events.
