# Boxlore — Product insight map (human questions)

Source: CEO / Head of Product / deep product-thinker pass over the Android app (no event names).  
Legend: **Must** = run the company · **Nice** = later optimization

## North-star / company health
- **Must** DAU / WAU / MAU
- **Must** Week-1 and week-4 retention for new installs
- **Must** % of opens that start playback that day
- **Must** % of starts that meaningfully complete an episode that day
- **Must** Listening hours per active user per week
- **Must** Median sessions/week retained vs churned
- **Must** Activation: finish onboarding + play within 24h
- **Must** Growth WoW and channel mix (Play / APK / share / unknown)
- **Must** % listening from subscribed shows vs discovery surfaces
- **Must** Whether power listeners are also promoters (NPS/review)
- **Nice** Daypart listening; library-size distribution; offline vs stream share; Auto vs phone; continue-only vs discovery returns

## Acquisition & first open
- **Must** Installs/day by channel; share/referrer → open → play shared content
- **Must** First open path: onboarding vs deeplink skip vs session restore
- **Must** Deeplink first-open conversion vs organic; install→open→first-play latency
- **Nice** Episode-share finish rate; legacy boxcast vs boxlore link volume; clip/timestamp land accuracy; day-0 never-return; OPML importers retain better?

## Onboarding & activation
- **Must** Start/complete rates; path mix (AI / genre / search / OPML) × activation lift
- **Must** Abandon step; shows subscribed during onboarding × retention sweet spot
- **Must** First post-onboarding play source; AI path long-term listening vs prettier funnel
- **Nice** AI turns; search redirect follow-through; duration×retention; archetypes; re-entry from Home; OPML parse success; region match; deeplink skip retainers

## Home / discovery
- **Must** First tap on Home (hero variants, mixtape, briefing, BYL, rails, Discover, Library)
- **Must** Which surfaces create starts and completions; adaptive vs Discover; cold/empty Home paths; daypart effect
- **Nice** Hero swipe; filter-by-show; import banner; briefing dismiss; BYL play-through; adaptive intent quality; tab-bounce without play

## Explore / search / learn
- **Must** Explore reach and contribution to starts; For You vs charts vs category vs search
- **Must** Show-keyword vs episode-semantic search → play/subscribe; zero-result recovery
- **Must** Learn reach; Learn session → play/queue; dismiss vs play vs queue vs info ratios
- **Nice** Vibes; tab switches; category entry; pagination depth; caught-up return; history restore; on-device rerank; region nudges

## Podcast & episode detail
- **Must** Visit → subscribe / play / download / bounce; episode detail action mix
- **Must** Subscribe-before vs after first play; per-show notification enable → return play; related-eps use
- **Nice** In-show search; hide completed; mark-all; intro/outro overrides; playback settings discovery; cross-promo; RSS-only quality; notes→play

## Playback
- **Must** Entry-point mix (full surface list including Auto, notification, share, restore, mixtape, Learn, …)
- **Must** Resume vs fresh; 25/50/75/complete rates; abandon timing × entry point
- **Must** After complete: smart-queue / same-show / pick / end; session restore play vs dismiss; time-to-first-audio / play fail rate
- **Nice** Early skip as rec signal; restart-after-resume; offline completion; background vs full player; video mode; sleep/late-night; smart-queue tiers & skips; Lore queue conflict choices

## Queue / history / downloads / offline
- **Must** Intentional queue use vs autofill; Downloads as habit; download success/fail reasons; Smart Downloads listen-through; per-show auto-download play
- **Nice** History as continue surface; clear/delete reasons; Play All; offline findability; Smart Download constraints; manual vs WM sync; reorder; Up Next conversion

## Library / subscriptions
- **Must** Sub count retained vs churned; time-to-first-play after subscribe; Library hub destinations that drive listening; unsubscribe patterns
- **Nice** List/grid/sort/genre; liked replay; RSS URL adds; notif % among subs; hide-completed prefs

## Player UX
- **Must** Non-1x listening share; sleep timer use; full vs mini player; chapters/transcripts use + auto-gen success
- **Nice** Seek durations; skip intro/outro; control-deck actions; coachmarks; video fullscreen; pause reasons; seek source; artwork theming

## Notifications / deep links / share
- **Must** Permission grant timing; new-episode open→play; announcement/What’s New act/dismiss; share volume → installs/opens; deeplink land + autoplay correctness
- **Nice** Post-restore FCM topics; debug/prod topics; share targets; Play Whats New suppression; channel silence

## Settings / privacy / backup
- **Must** Settings reach and which categories change; OPML/JSON export-import continuity; privacy resets impact
- **Nice** Theme popularity; region change; feedback categories; NPS/review timing and conversion; ranking reset motives; feature overlays

## Android Auto
- **Must** Auto user % and listening-minute share; browse-root → play; voice/Play All success; in-car actions (like/queue/complete)
- **Nice** Drive Mix friction; post-disconnect continue; artwork latency; intro skip in car; audio-focus interrupts

## Retention / churn / trust / ranking
- **Must** Healthy weekly habit shape; early predictors of d7/d30; churn triggers; last success vs last failure
- **Must** Play/download/RSS/catalog-miss/resume-desync rates that kill delight
- **Must** Personalized rails vs charts outcomes; which +/- signals move rankings; cold-start until personalization helps
- **Nice** Serial vs news retain; Smart Downloads×commute; briefing dismiss = power user?; queue-dry ends; discovery-heavy vs sub-only churn; return triggers after absence; diversity rerank; Learn→Home taste transfer; backup ranking coherence

## Top 10 blind spots (highest leverage)
1. Real activation definition × onboarding path lift  
2. Where retained users actually start listening  
3. Why new users fail to play within 24h  
4. Personalized Home/For You vs charts (starts, completes, W4)  
5. Learn: growth engine or side quest  
6. New-episode notifications: retention loop vs trust risk  
7. Offline/Smart Downloads: habit vs waste  
8. Android Auto listening share and silent Autochurn  
9. Ranked delight-killers in the wild  
10. Share/deep-link growth loop worth  
