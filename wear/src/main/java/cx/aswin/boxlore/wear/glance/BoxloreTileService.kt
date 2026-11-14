package cx.aswin.boxlore.wear.glance

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import cx.aswin.boxlore.wear.MainActivity
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.WatchApplication
import cx.aswin.boxlore.wear.ui.WatchRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The boxlore tile: the episode to jump back into, with its artwork and Resume on the edge of the
 * screen. With nothing in progress it offers to open the app instead. It is drawn from the saved
 * library, so it works with the app closed. It keeps boxlore's own colours on every watch face.
 */
class BoxloreTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        answer {
            val glance = glance()
            // Asked for before the layout is built, so the layout only names a picture that exists.
            val artwork = glance?.let { TileArtwork.of(TileRules.artworkId(it), it.artworkUrl, this) }
            val layout =
                materialScope(this, requestParams.deviceConfiguration, allowDynamicTheme = false, defaultColorScheme = TileColors.scheme) {
                    if (glance == null) empty(this@BoxloreTileService) else jumpBackIn(this@BoxloreTileService, glance, hasArtwork = artwork != null)
                }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(TileRules.resourcesVersion(glance, hasArtwork = artwork != null))
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        answer {
            // The version is the picture's name, so the picture handed over is always the one the layout asked for.
            val id = requestParams.version
            val glance = glance()?.takeIf { TileRules.artworkId(it) == id }
            val artwork = TileArtwork.of(id, glance?.artworkUrl, this)
            val resources = ResourceBuilders.Resources.Builder().setVersion(id)
            if (artwork != null) resources.addIdToImageMapping(id, artwork.resource())
            resources.build()
        }

    private fun glance(): WatchGlance? = (application as? WatchApplication)?.container?.glance?.invoke()

    private fun <T> answer(block: suspend () -> T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch { runCatching { block() }.onSuccess { completer.set(it) }.onFailure { completer.setException(it) } }
            "boxlore tile"
        }

    companion object {
        private const val ARTWORK_DP = 52f
        private const val ARTWORK_CORNER_DP = 15f
        private const val GAP_DP = 4f

        /** Redraws the tile after the episode to jump back into, or its time left, changed. */
        fun requestUpdate(context: Context) = getUpdater(context).requestUpdate(BoxloreTileService::class.java)

        private fun MaterialScope.empty(context: Context): LayoutElement =
            primaryLayout(
                titleSlot = { text(context.getString(R.string.wear_app_name).layoutString) },
                mainSlot = { text(context.getString(R.string.wear_jump_back_in_empty).layoutString, typography = Typography.TITLE_MEDIUM, maxLines = 2) },
                bottomSlot = {
                    textEdgeButton(
                        onClick = clickable(open(context, resume = null)),
                        labelContent = { text(context.getString(R.string.wear_tile_open).layoutString) },
                    )
                },
            )

        private fun MaterialScope.jumpBackIn(context: Context, glance: WatchGlance, hasArtwork: Boolean): LayoutElement {
            val timeLeft = context.getString(R.string.wear_minutes_left, glance.minutesLeft)
            val detail = GlanceRules.detail(glance.showTitle, timeLeft) { show, time -> context.getString(R.string.wear_home_state_and_time, show, time) }
            return primaryLayout(
                titleSlot = { text(context.getString(R.string.wear_jump_back_in).layoutString) },
                mainSlot = { episode(glance, detail, hasArtwork) },
                bottomSlot = {
                    textEdgeButton(
                        onClick = clickable(open(context, resume = glance.episodeId)),
                        labelContent = { text(context.getString(R.string.wear_resume).layoutString) },
                    )
                },
            )
        }

        /**
         * The artwork, the episode's name and the show with the time left, stacked in the middle.
         * The name is what the listener looks for, so without artwork it takes a second line.
         */
        private fun MaterialScope.episode(glance: WatchGlance, detail: String, hasArtwork: Boolean): LayoutElement {
            val column = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            if (hasArtwork) {
                column.addContent(artwork(TileRules.artworkId(glance)))
                column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(GAP_DP)).build())
            }
            column.addContent(text(glance.title.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = if (hasArtwork) 1 else 2))
            column.addContent(text(detail.layoutString, typography = Typography.BODY_SMALL, color = colorScheme.onSurfaceVariant, maxLines = 1))
            return column.build()
        }

        private fun artwork(id: String): LayoutElement {
            val corner = ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(ARTWORK_CORNER_DP)).build()
            return LayoutElementBuilders.Image.Builder()
                .setResourceId(id)
                .setWidth(DimensionBuilders.dp(ARTWORK_DP))
                .setHeight(DimensionBuilders.dp(ARTWORK_DP))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(ModifiersBuilders.Background.Builder().setCorner(corner).build()).build())
                .build()
        }

        /** Opens the app to carry on with the episode [resume] names; without one, the app's Home. */
        private fun open(context: Context, resume: String?): ActionBuilders.Action {
            val extras = if (resume != null) mapOf(WatchRoutes.EXTRA_RESUME to ActionBuilders.stringExtra(resume)) else emptyMap()
            return ActionBuilders.launchAction(ComponentName(context, MainActivity::class.java), extras)
        }
    }
}

/** How the tile names its picture, which is also how the watch knows when to ask for a new one. */
internal object TileRules {
    const val NO_ARTWORK = "none"

    /** One name per episode, so a new episode never shows the last one's artwork. */
    fun artworkId(glance: WatchGlance): String = "art-${glance.episodeId}"

    fun resourcesVersion(glance: WatchGlance?, hasArtwork: Boolean): String = if (glance != null && hasArtwork) artworkId(glance) else NO_ARTWORK
}

/** boxlore's watch colours for the tile; the same roles the app's own theme uses. */
internal object TileColors {
    val scheme =
        ColorScheme(
            primary = LayoutColor(0xFF9C8CFF.toInt()),
            primaryDim = LayoutColor(0xFF8576E0.toInt()),
            primaryContainer = LayoutColor(0xFF3F347B.toInt()),
            onPrimary = LayoutColor(0xFF21164B.toInt()),
            onPrimaryContainer = LayoutColor(0xFFECE7FF.toInt()),
            onSurface = LayoutColor(0xFFE6E6EE.toInt()),
            onSurfaceVariant = LayoutColor(0xFFC8C5D3.toInt()),
        )
}
