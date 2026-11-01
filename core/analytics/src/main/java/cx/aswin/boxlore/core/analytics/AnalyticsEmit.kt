package cx.aswin.boxlore.core.analytics

import android.util.Log
import com.posthog.PostHog

/**
 * Single egress for PostHog captures. Drops names outside glossary Phase A∪B
 * (except `$set` person properties). Tests may replace [eventSink] / [personSetSink].
 * Episode events get `recipe_id` / `recipe_ids` here when known ([EpisodeRecipeIds]).
 * Events this install doesn't send are dropped here too ([AnalyticsPolicy]); sampled ones get
 * `sample_percent`.
 */
internal object AnalyticsEmit {
    private const val TAG = "AnalyticsEmit"

    @Volatile
    var eventSink: (String, Map<String, Any>) -> Unit = { event, properties ->
        PostHogStartup.runWhenReady { PostHog.capture(event = event, properties = properties) }
    }

    @Volatile
    var personSetSink: (Map<String, Any>) -> Unit = { userProperties ->
        PostHogStartup.runWhenReady {
            PostHog.capture(event = AnalyticsGlossary.PERSON_SET_EVENT, userProperties = userProperties)
        }
    }

    /** PostHog error tracking (`$exception`); [event] properties ride along. */
    @Volatile
    var exceptionSink: (Throwable, Map<String, Any>) -> Unit = { error, properties ->
        PostHogStartup.runWhenReady { PostHog.captureException(error, properties) }
    }

    /** False while a recording sink that ignores event levels is installed. */
    @Volatile
    private var applyLevels = true

    /**
     * Test helper: install a recording sink; returns captured event names + props. Wiring tests see
     * every event whatever its level; pass [applyLevels] to test the levels themselves.
     */
    fun installRecordingSink(recorder: MutableList<Pair<String, Map<String, Any>>>, applyLevels: Boolean = false,): () -> Unit {
        val previousEvent = eventSink
        val previousPerson = personSetSink
        val previousException = exceptionSink
        val previousLevels = this.applyLevels
        eventSink = { event, props -> recorder.add(event to props) }
        personSetSink = { props -> recorder.add(AnalyticsGlossary.PERSON_SET_EVENT to props) }
        exceptionSink = { _, props -> recorder.add(EXCEPTION_EVENT to props) }
        this.applyLevels = applyLevels
        return {
            eventSink = previousEvent
            personSetSink = previousPerson
            exceptionSink = previousException
            this.applyLevels = previousLevels
        }
    }

    fun event(name: String, properties: Map<String, Any> = emptyMap(),) {
        if (!AnalyticsGlossary.isAllowedEvent(name)) {
            Log.w(TAG, "Dropped non-glossary event: $name")
            return
        }
        val extra = if (applyLevels) AnalyticsPolicy.admit(name) ?: return else emptyMap()
        eventSink(name, EpisodeRecipeIds.withRecipeIds(name, properties + extra))
    }

    fun personSet(properties: Map<String, Any>) {
        personSetSink(properties)
    }

    fun exception(error: Throwable, properties: Map<String, Any> = emptyMap(),) {
        exceptionSink(error, properties)
    }

    /** How a recording sink names error-tracking captures. */
    const val EXCEPTION_EVENT = "\$exception"
}
