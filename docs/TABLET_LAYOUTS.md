# Tablet and foldable layout implementation

Status: phases 1–4 are committed locally. Phase 5 passed its complete gate and shell review. Phase 6 is in progress.

## Scope and preservation

Phone geometry takes priority. The frozen 66-image phone matrix includes 360×800, 412×915 and 800×360dp, light/dark. Layout changes must pass exact dimension and ARGB equality; baselines are never refreshed. Ranking, playback, storage identities and route attribution retain their existing owners.

## Specs

| Window | Navigation | Poster packing | Card limits | Panes |
| :--- | :--- | :--- | :--- | :--- |
| Width <600dp or height <480dp | Existing phone chrome | Existing screen counts | Original caller constraints | Existing single viewport |
| 600–839dp and height ≥480dp | Rail | About 3 columns at 600dp after rail | Shared tablet families below | Single content pane |
| 840–1199dp portrait | Rail | About 4 columns at 840dp after rail | Same caps | Single content pane |
| Width ≥840dp, landscape, height ≥480dp | Rail | Recomputed for primary pane | Same caps | Primary plus optional 360–520dp context, 16dp gap |
| Width ≥1200dp | Rail | 5+ when the available pane allows | Same caps | Same split rule |
| Separating/occluding hinge | Per contiguous region | Current pane width | Same caps | No pane crosses hinge; undersized context closes |

| Family | Tablet packing minimum | Maximum surface width | Aspect / behavior |
| :--- | ---: | ---: | :--- |
| Hero / feature | 280dp | 480dp | Existing internal artwork/layout; carousel prefers 320dp |
| Poster / show | 140dp | 208dp | Existing square artwork/title foot; legacy fixed-height previews stay intact |
| Episode | 280dp | 720dp | Existing horizontal row/card layout |
| Rail item | 140dp | 310dp | Existing fixed dimensions, including 310dp mix and 260dp Brief chips |
| Reading | 280dp | 720dp | Natural text height; Lore deck retains its smaller 520dp cap |
| Settings / utility | 280dp | 720dp | Centered column; Account retains 600dp, Echo cards retain 420dp |

Minimums guide grid packing rather than forcing overflow in narrow panes. Compact specs preserve the complete caller constraints. Surface bounds work inside tight `fillMaxWidth` slots and center the painted child. Controls, badges and artwork previews inside a card inherit its family; they are not additional independently sized cards.

## Card inventory

The index includes named card containers, their loading forms, and named internal card parts (which inherit their container's bounds). Anonymous grouped panels and rails follow in the supplemental inventory. Locations refer to the current implementation and are refreshed at handoff.

| Module | Card / part | Family | Declaration | Width owner |
| :--- | :--- | :--- | :--- | :--- |
| `core/designsystem` | `CuratedEpisodeCard` | Hero / Poster | `core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/CuratedEpisodeCard.kt:24` | Shared presentation variant |
| `core/designsystem` | `ExpressiveFeedMediaCard` | Hero / Poster | `core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/ExpressiveFeedMediaCard.kt:64` | Shared presentation variant |
| `core/designsystem` | `FeedMediaCardSkeleton` | Hero / Poster | `core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/ExpressiveFeedMediaCard.kt:104` | Shared presentation variant |
| `core/designsystem` | `FeedMediaCard` | Hero / Poster | `core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/FeedMediaCard.kt:47` | Shared presentation variant |
| `core/designsystem` | `ToggleCard` | Utility | `core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/PrivacyConsentDialog.kt:327` | Bounded parent or family modifier |
| `feature/home` | `LogToggleCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/AdaptiveLearnerDebugSection.kt:251` | Bounded parent or family modifier |
| `feature/home` | `FacetGroupCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/AdaptiveLearnerDebugSection.kt:492` | Bounded parent or family modifier |
| `feature/home` | `SectionCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/AdaptiveLearnerDebugSection.kt:722` | Bounded parent or family modifier |
| `feature/home` | `InfoCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/AdaptiveLearnerDebugSection.kt:746` | Bounded parent or family modifier |
| `feature/home` | `HistoryDebugCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/DebugScreen.kt:338` | Bounded parent or family modifier |
| `feature/home` | `PodcastDebugCard` | Utility | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/DebugScreen.kt:373` | Bounded parent or family modifier |
| `feature/home` | `ClassicHeroCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/ClassicHeroCards.kt:81` | Bounded parent or family modifier |
| `feature/home` | `ClassicHeroGridCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/ClassicHeroCards.kt:222` | Bounded parent or family modifier |
| `feature/home` | `DailyBriefingCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/DailyBriefingCard.kt:80` | Bounded parent or family modifier |
| `feature/home` | `FeaturedCuratedCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/FeaturedCuratedCard.kt:24` | Bounded parent or family modifier |
| `feature/home` | `HeroCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HeroCard.kt:56` | Bounded parent or family modifier |
| `feature/home` | `HeroCardContext` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HeroCardContext.kt:74` | Bounded parent or family modifier |
| `feature/home` | `HeroGridCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HeroGridCard.kt:70` | Bounded parent or family modifier |
| `feature/home` | `MixtapeEpisodeCard` | Rail item | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HomeMixEpisodeCard.kt:53` | Bounded parent or family modifier |
| `feature/home` | `HeroSkeletonCard` | Hero | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HomeSkeleton.kt:136` | Bounded parent or family modifier |
| `feature/home` | `HomeMediaCardSkeleton` | Hero / Poster | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/HomeSkeleton.kt:388` | Shared presentation variant |
| `feature/home` | `OfflineMixEpisodeCard` | Rail item | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/LibrarySectionMixtape.kt:489` | Bounded parent or family modifier |
| `feature/home` | `ViewAllDownloadsCard` | Rail item | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/LibrarySectionMixtape.kt:550` | Bounded parent or family modifier |
| `feature/home` | `PodcastCard` | Poster | `feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/PodcastCard.kt:27` | Bounded parent or family modifier |
| `feature/settings` | `SettingsCategoryCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/components/SettingsRows.kt:59` | Bounded parent or family modifier |
| `feature/settings` | `ThemeLookCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/components/ThemeLookCard.kt:38` | Bounded parent or family modifier |
| `feature/settings` | `AutoDownloadBackgroundSettingsCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/downloads/AutoDownloadBackgroundSettingsCard.kt:20` | Bounded parent or family modifier |
| `feature/settings` | `FeedbackStatusCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/feedback/FeedbackSuccessView.kt:207` | Bounded parent or family modifier |
| `feature/settings` | `FeedbackSummaryCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/feedback/FeedbackSuccessView.kt:276` | Bounded parent or family modifier |
| `feature/settings` | `AccountPrivacyCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/AccountAuthUiComponents.kt:188` | Bounded parent or family modifier |
| `feature/settings` | `ReadyCloudSyncCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/AccountCloudSyncComponents.kt:142` | Bounded parent or family modifier |
| `feature/settings` | `PrimaryAuthCardBody` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/AccountSignedOutContent.kt:163` | Bounded parent or family modifier |
| `feature/settings` | `EmailLinkSentAddressCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/AccountSignedOutContent.kt:316` | Bounded parent or family modifier |
| `feature/settings` | `EmailLinkSentSpamCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/AccountSignedOutContent.kt:349` | Bounded parent or family modifier |
| `feature/settings` | `EmailVerificationStepsCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/EmailVerificationPendingSection.kt:196` | Bounded parent or family modifier |
| `feature/settings` | `EmailVerificationAddressCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/EmailVerificationPendingSection.kt:253` | Bounded parent or family modifier |
| `feature/settings` | `EmailVerificationSpamWarningCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/EmailVerificationPendingSection.kt:286` | Bounded parent or family modifier |
| `feature/settings` | `PromiseCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/PrivacySettingsPage.kt:246` | Bounded parent or family modifier |
| `feature/settings` | `AnalyticsCardActionButton` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/PrivacySettingsPage.kt:465` | Bounded parent or family modifier |
| `feature/settings` | `AnalyticsCardHeader` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/PrivacySettingsPage.kt:489` | Bounded parent or family modifier |
| `feature/settings` | `AnalyticsCardFooter` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/PrivacySettingsPage.kt:521` | Bounded parent or family modifier |
| `feature/settings` | `AnalyticsControlCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/PrivacySettingsPage.kt:554` | Bounded parent or family modifier |
| `feature/settings` | `SupportMissionCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/SupportDevelopmentPage.kt:800` | Bounded parent or family modifier |
| `feature/settings` | `CloudSyncCard` | Utility | `feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/SyncAndBackupsPage.kt:167` | Bounded parent or family modifier |
| `feature/library` | `FolderTechnologyWarningCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/FolderEditComponents.kt:232` | Bounded parent or family modifier |
| `feature/library` | `FolderSizeCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/FolderEditComponents.kt:532` | Bounded parent or family modifier |
| `feature/library` | `FolderCompactCoverStyleCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/FolderEditComponents.kt:583` | Bounded parent or family modifier |
| `feature/library` | `FolderOrganizationCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/FolderEditComponents.kt:705` | Bounded parent or family modifier |
| `feature/library` | `LibraryMenuCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/LibraryScreen.kt:322` | Bounded parent or family modifier |
| `feature/library` | `LibraryCardCollage` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/LibraryScreen.kt:389` | Bounded parent or family modifier |
| `feature/library` | `LibraryPodcastCard` | Poster | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/LibraryScreen.kt:505` | Bounded parent or family modifier |
| `feature/library` | `PodcastListShowCard` | Episode | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/downloads/DownloadModels.kt:90` | Bounded parent or family modifier |
| `feature/library` | `SmartDownloadsDashboardCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/downloads/SmartDownloadsDashboard.kt:119` | Bounded parent or family modifier |
| `feature/library` | `Compact1x1FolderCard` | Poster | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/CompactSubscriptionFolderCards.kt:69` | Bounded parent or family modifier |
| `feature/library` | `PinnedEnlargedFolderCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionFolderCards.kt:74` | Bounded parent or family modifier |
| `feature/library` | `FolderOverflowSlotCard` | Poster | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionFolderCards.kt:367` | Bounded parent or family modifier |
| `feature/library` | `SubscriptionGridCard` | Poster | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionRows.kt:403` | Bounded parent or family modifier |
| `feature/library` | `AutoOrganizeCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionSortSheet.kt:197` | Bounded parent or family modifier |
| `feature/library` | `ShowsSortCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionSortSheet.kt:256` | Bounded parent or family modifier |
| `feature/library` | `FolderSortCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionSortSheet.kt:300` | Bounded parent or family modifier |
| `feature/library` | `IntraFolderSortCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionSortSheet.kt:326` | Bounded parent or family modifier |
| `feature/library` | `SortSectionCard` | Utility | `feature/library/src/main/java/cx/aswin/boxlore/feature/library/subscriptions/SubscriptionSortSheet.kt:351` | Bounded parent or family modifier |
| `feature/info` | `CrossPromotionCard` | Episode | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/CrossPromotionCard.kt:49` | Bounded parent or family modifier |
| `feature/info` | `EpisodeDescriptionCard` | Reading | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/EpisodeDescriptionCard.kt:47` | Bounded parent or family modifier |
| `feature/info` | `SingleTrailerCard` | Episode | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/components/PodcastTrailerCards.kt:70` | Bounded parent or family modifier |
| `feature/info` | `TrailerStackCard` | Episode | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/components/PodcastTrailerCards.kt:142` | Bounded parent or family modifier |
| `feature/info` | `EpisodeInfoMoreLikeThisCard` | Poster | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/sections/EpisodeInfoRecommendationCards.kt:16` | Bounded parent or family modifier |
| `feature/info` | `EpisodeInfoMoreFromPodcastCard` | Episode | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/sections/EpisodeInfoRecommendationCards.kt:30` | Bounded parent or family modifier |
| `feature/info` | `RecommendedPodcastCard` | Rail item | `feature/info/src/main/java/cx/aswin/boxlore/feature/info/sections/PodcastInfoDescriptionSection.kt:235` | Bounded parent or family modifier |
| `feature/explore` | `CuriosityCardStack` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/CuriosityCardStack.kt:57` | Existing 520dp deck cap |
| `feature/explore` | `CuriosityCardContent` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/CuriosityCardStack.kt:218` | Existing 520dp deck cap |
| `feature/explore` | `LoreCardSource` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/CuriosityCardStack.kt:312` | Existing 520dp deck cap |
| `feature/explore` | `LoreCardActionBar` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/CuriosityCardStack.kt:409` | Existing 520dp deck cap |
| `feature/explore` | `LoreStateCard` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/LearnScreen.kt:318` | Bounded parent or family modifier |
| `feature/explore` | `LoreCardText` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/LoreCardText.kt:80` | Existing 520dp deck cap |
| `feature/explore` | `LoreCardTextWithFooter` | Reading | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/LoreCardText.kt:115` | Existing 520dp deck cap |
| `feature/explore` | `ExploreHeroCard` | Hero | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/components/ExploreBrowseHeaders.kt:52` | Bounded parent or family modifier |
| `feature/explore` | `ExploreVibeCard` | Poster | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/components/ExploreCards.kt:137` | Bounded parent or family modifier |
| `feature/explore` | `ExploreBrowsePodcastCard` | Hero / Poster | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/components/ExploreDiscoveryCards.kt:29` | Shared presentation variant |
| `feature/explore` | `ExploreBrowseCardSkeleton` | Hero / Poster | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/components/ExploreDiscoveryCards.kt:72` | Shared presentation variant |
| `feature/explore` | `ExploreSearchEpisodeCard` | Hero / Poster | `feature/explore/src/main/java/cx/aswin/boxlore/feature/explore/components/ExploreSearchResultCards.kt:29` | Shared presentation variant |
| `feature/onboarding` | `SuggestionShowCard` | Poster | `feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/AiSuggestionCards.kt:75` | Fixed 140dp lane |
| `feature/onboarding` | `SuggestionCardsSkeleton` | Poster | `feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/AiSuggestionsScreen.kt:362` | Fixed 140dp lane |
| `feature/onboarding` | `EchoDeckCard` | Hero | `feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/EchoDeckViews.kt:88` | Existing 420dp deck cap |
| `feature/onboarding` | `EchoDeckBackCard` | Hero | `feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/EchoDeckViews.kt:174` | Existing 420dp deck cap |
| `feature/onboarding` | `EchoHoloCard` | Hero | `feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/EchoHoloCard.kt:73` | Existing 420dp deck cap |
| `feature/briefing` | `BriefingStoryCard` | Reading | `feature/briefing/src/main/java/cx/aswin/boxlore/feature/briefing/BriefingStoryCard.kt:79` | Bounded parent or family modifier |
| `feature/briefing` | `StoryCardTop` | Reading | `feature/briefing/src/main/java/cx/aswin/boxlore/feature/briefing/BriefingStoryCard.kt:115` | Bounded parent or family modifier |
| `feature/briefing` | `StoryCardHeader` | Reading | `feature/briefing/src/main/java/cx/aswin/boxlore/feature/briefing/BriefingStoryCard.kt:123` | Bounded parent or family modifier |
| `feature/history` | `MetricCardBody` | Utility | `feature/history/src/main/java/cx/aswin/boxlore/feature/history/HistoryInsightCarousel.kt:218` | Bounded parent or family modifier |
| `feature/history` | `MetricCardText` | Utility | `feature/history/src/main/java/cx/aswin/boxlore/feature/history/HistoryInsightCarousel.kt:280` | Bounded parent or family modifier |
| `feature/history` | `TopShowCardBody` | Episode | `feature/history/src/main/java/cx/aswin/boxlore/feature/history/HistoryInsightCarousel.kt:323` | Bounded parent or family modifier |
| `feature/history` | `ListeningTimeCard` | Utility | `feature/history/src/main/java/cx/aswin/boxlore/feature/history/HistoryStatsCards.kt:57` | Bounded parent or family modifier |
| `feature/player` | `MiniPlayerCard` | Utility | `feature/player/src/main/java/cx/aswin/boxlore/feature/player/v2/MiniPlayerV2.kt:315` | Bounded parent or family modifier |
| `feature/player` | `NotesPreviewCard` | Reading | `feature/player/src/main/java/cx/aswin/boxlore/feature/player/v2/NotesPreviewCard.kt:42` | Bounded parent or family modifier |
| `feature/player` | `PlayerArtworkCard` | Hero | `feature/player/src/main/java/cx/aswin/boxlore/feature/player/v2/PlayerHero.kt:276` | Bounded parent or family modifier |
| `feature/player` | `UpNextCard` | Episode | `feature/player/src/main/java/cx/aswin/boxlore/feature/player/v2/UpNextCard.kt:44` | Bounded parent or family modifier |

## Supplemental surfaces and arrangement

| Surface | Family / sizing owner | Source |
| :--- | :--- | :--- |
| Hero carousel and loading rail | Hero; several bounded cards on wide viewports | `feature/home/.../components/HeroCarousel.kt`, `HomeSkeleton.kt` |
| Your shows module, connected episode rows and footer | Utility parent ≤720dp; rows inherit bounds; selectors retain 68dp tiles | `feature/home/.../components/LibrarySection.kt`, `DenseEpisodeRows.kt`, `YourShowsSelectors.kt` |
| Home discovery rails | Poster; existing 176/208dp maxima and stable ordering | `feature/home/.../components/HomeDiscoveryRail.kt`, `HomeFeedSpacing.kt` |
| Daily/offline mix rails | Rail item; existing 310dp cards, 148dp downloads action | `feature/home/.../components/LibrarySectionMixtape.kt`, `HomeMixLayout.kt` |
| Because you like seed | Hero ≤480dp, related rails retain their own bounds | `feature/home/.../components/BecauseYouLikeSection.kt` |
| Setup tiles | Utility; bounded banner parent | `feature/home/.../HomeImportBanner.kt` |
| SettingsGroup, tips and privacy callouts | Utility; ordinary shared SettingsScaffold ≤720dp | `feature/settings/.../components/SettingsRows.kt`, `pages/PrivacySettingsPage.kt` |
| Account auth, sync, verification and privacy panels | Utility; existing AccountPageScaffold ≤600dp | `feature/settings/.../pages/AccountPageContent.kt` and related account components |
| Smart Downloads anonymous cards | Utility; newly bounded parent ≤720dp | `feature/settings/.../downloads/SmartDownloadsSettingsScreen.kt` |
| Feedback draft, diagnostic and summary panels | Utility; newly bounded form/success parent ≤720dp | `feature/settings/.../feedback/FeedbackScreen.kt`, `FeedbackSuccessView.kt` |
| Custom theme previews and controls | Utility; existing two columns within 1100dp, stacked 600dp | `feature/settings/.../pages/CustomThemeEditor.kt`, `CustomThemePreview.kt`, `CustomThemeColorControls.kt` |
| Folder preview show covers | Poster ≤208dp; pinned folder Utility ≤720dp | `feature/library/.../subscriptions/SubscriptionFolderCards.kt` |
| Episode/liked/download/subscription rows | Episode; bounded 720dp list parents | `feature/library/.../LikedEpisodesScreen.kt`, `downloads/DownloadedShowEpisodesScreen.kt`, `subscriptions/SubscriptionTabContents.kt` |
| Info episode rows, chapter rows and More from | Episode; bounded detail parent ≤720dp | `feature/info/.../components/EpisodeListItem.kt`, `MoreFromEpisodeSection.kt`, `EpisodeChaptersSection.kt` |
| Show About/description and promotions | Reading/Episode; bounded detail parent ≤720dp | `feature/info/.../components/ArtworkInfoTheme.kt`, `sections/PodcastInfoDescriptionSection.kt` |
| Episode recommendation rail | Poster; existing 160dp cards | `feature/info/.../components/EpisodeRecommendationSection.kt` |
| Explore related shows | Poster; existing 104dp cards and eight-show cap | `feature/explore/.../components/ExploreCards.kt` |
| Lore history row | Episode ≤720dp | `feature/explore/.../LearnHistoryScreen.kt` |
| Echo answers, activity/length cards and suggestion summary | Utility; bounded screen parent ≤720dp, height tiers retained | `feature/onboarding/.../EchoAnswers.kt`, `GenreOnboardingScreen.kt`, `AiSuggestionsScreen.kt` |
| Onboarding popular-show grid | Poster; adaptive count, ≤208dp card | `feature/onboarding/.../SearchOnboardingScreen.kt` |
| Legacy AI choice/loading/chat panels | Utility; inactive legacy helpers, current onboarding delegates to bounded Echo | `feature/onboarding/.../AiOnboardingComponents.kt`, `AiChatInputPanel.kt` |
| Brief related episode chips | Rail item; existing fixed 260dp | `feature/briefing/.../BriefingStoryComponents.kt` |
| Brief story loading and reporting panels | Reading ≤720dp | `feature/briefing/.../BriefingLoading.kt`, `BriefingScreen.kt` |
| History metrics, activity/time graphs and timeline | Utility/Reading/Episode; bounded 720dp parent | `feature/history/.../HistorySuccessList.kt`, `HistoryActivityGraphs.kt`, `HistoryListItems.kt` |
| Queue continuation banner | Episode; player pane/sheet bounds added in phase 4 | `feature/player/.../QueueScreen.kt` |
| Privacy consent choices | Utility; platform dialog bounds | `core/designsystem/.../components/PrivacyConsentDialog.kt` |

## Captures

- Immutable phone before: `screenshots/baselines/phone/` (66 PNGs).
- Phone current renders: `build/reports/phone-goldens/actual/`.
- Tablet before: `output/tablet-layouts/before/` (132 PNGs), also `build/reports/tablet-captures/before/`.
- Card-sizing review pass: `output/tablet-layouts/phase3-card-sizing/` (132 images). These are work in progress; the player and rail/split shell are not applied yet. Final after captures are not done.
- Player review pass: `output/tablet-layouts/phase4-player/` (12 images). Portrait controls remain visible at 600dp; tabletop art and transport stay on opposite sides of the synthetic hinge. Final shell captures are not done.
- Split-shell review pass: `output/tablet-layouts/phase5-shell/` (48 images). This pass includes rail, primary/context player, portrait docking and tabletop shell placement. Phase 6 refinements are not applied.
- Teal artwork is a fixed, in-memory test image; fixtures perform no network requests. Synthetic hinges do not prove device posture handling.

## Dependency change

The unused adaptive navigation-suite was replaced by stable Adaptive `adaptive` 1.1.0 and an explicit Window 1.4.0 dependency at the shell. Newer stable Adaptive releases would raise resolved Compose dependencies. The release dependency snapshot confirms that UI/runtime 1.9.2, Foundation 1.8.1 and Material 3 1.5.0-alpha11 remain unchanged. The unused `adaptive-layout` artifact was removed after the shell implementation; it contributes no runtime layout code. Test-support adds no release dependencies.

## Verification ledger

Use JDK 17 and `GRADLE_USER_HOME=$HOME/.gradle` with the wrapper. Each phase runs:

```bash
./gradlew detekt ktlintCheck testDebugUnitTest :koverVerifyMerged :app:dependencyGuard :core:catalog:dependencyGuard :core:playback:dependencyGuard lintDebug -PphoneGoldens=verify --continue --max-workers=2
bash scripts/ci/check-feature-no-boxlore-database.sh
bash scripts/ci/check-feature-no-posthog.sh
```

- Phase 1: complete; 66 phone images committed alone. Full gate passed after hand-fixing new-test lint.
- Phase 2: complete; all units, coverage, dependency guards, Android lint and 66 phone images passed. Two new-model ktlint tasks failed in the first full run, were fixed by hand, and `detekt ktlintCheck :core:designsystem:testDebugUnitTest --tests '*BoxloreWindowLayoutTest' installDebug --max-workers=2` passed (9 window cases). Installed on the connected phone; no interaction claim is made.
- Phase 3: complete full gate passed, including merged coverage, dependency guards, Android lint and all 66 phone images. Focused loose/tight slot tests passed. The first pass caught an Echo capture-wrapper start-frame change and a loose-slot viewport issue; both were fixed with the phone baselines unchanged. Installed on the connected phone.
- Phase 4: the full gate passed in 2m21s, including all 66 unchanged phone images, JVM player/posture/video/state tests, merged coverage, static checks and dependency guards. Visual review caught clipped portrait controls and a cramped tabletop empty message; both were fixed and reverified. `installDebug` was attempted after a successful debug build but the phone had disconnected (`No connected devices`), so phase 4 device installation and interaction checks are not done.
- Phase 5: the frozen-source full gate passed in 2m34s, including all 66 unchanged phone images, retained pane/navigation/reflow tests, coverage, dependency guards and Android lint. Both feature-boundary scripts passed. Shell captures were reviewed separately.
- Tablet before: `:app:testDebugUnitTest :feature:explore:testDebugUnitTest :feature:info:testDebugUnitTest :feature:player:testDebugUnitTest :feature:onboarding:testDebugUnitTest :feature:settings:testDebugUnitTest -PtabletCaptures=before --tests '*PhoneGoldenTest' --max-workers=2` passed, 132 images.
- Remote CI, real folding hardware and finished tablet interaction validation: not done.

## Remaining work and proposals

Rail/split context navigation and saved pane state are implemented. Modal adaptation, keyboard/secondary-click behavior and final captures are not done yet. The player has bounded portrait/column layouts, fold-safe tabletop regions and saved Up next / Chapters / Transcript / Notes tabs. Existing playback and supporting-content actions are reused. No card internal redesign has been introduced. Potential future proposals: a wide editorial briefing composition, a richer expanded library hub, and a wider Lore reading format require separate product review; the current work retains their existing designs.

## Local state

Branch `one/tablet-layouts` starts at `66efd4d3`. Phase 1 commit `0bb5744b`; phase 2 commit `8defb51d`; phase 3 commit `7f827380`; phase 4 commit `8d7eebee`. All work remains local. No push, PR, merge or deployment is part of this task.
