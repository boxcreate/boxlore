'use strict';

/**
 * Catalog row values built from Podcast Index data, scrubbed before they are bound.
 *
 * turso.mapArgType refuses objects, so one odd optional field would otherwise fail the
 * whole write (a stage 2 batch of 50 shows, or a show's tip update every run). Optional
 * text becomes '' or null and bad numbers become 0, the same as a missing field. IDs are
 * passed through untouched: a broken ID should fail the write, not be guessed.
 * Added in P0 of the own-episode-IDs plan (plan section 14).
 */

const { asScalarString, asNonNegInt } = require('./scalars');
const text = require('./text');

/** Optional text: '' when missing or not a scalar. */
const str = (v, fallback = '') => asScalarString(v, fallback);
/** Optional text stored as NULL when missing. */
const orNull = (v) => asScalarString(v, '') || null;
/** Structured fields are stored as JSON text on purpose. */
const jsonOrNull = (v) => (v && typeof v === 'object' ? JSON.stringify(v) : null);

/** An episode ID must be a non-empty string or a number; anything else fails the write. */
function episodeIdText(v) {
    if ((typeof v === 'string' && v.trim() !== '') || (typeof v === 'number' && Number.isFinite(v))) return String(v);
    throw new TypeError(`Invalid episode id (${v === null ? 'null' : typeof v})`);
}

/**
 * Stage 2: PI's category map ({ "1": "News", ... }) as "News, Politics" before sorting.
 * Non-scalar or junk values are dropped so the rest survive.
 */
function categoryList(categories) {
    if (!categories || typeof categories !== 'object') return '';
    return Object.values(categories).map((c) => str(c).trim()).filter(Boolean).join(', ');
}

/**
 * Stage 2: values for an API import, in cfg.PODCAST_IMPORT_COLUMNS order.
 * @param {object} feed           PI podcast object
 * @param {string|number} itunesId the chart's iTunes ID, used when PI has none
 * @param {string} categories      sorted category list
 */
function apiImportArgs(feed, itunesId, categories) {
    return [
        feed.id,
        feed.itunesId || itunesId,
        str(feed.title) || 'Unknown Title',
        str(feed.author) || 'Unknown Author',
        str(feed.description),
        str(feed.image) || str(feed.artwork),
        str(feed.url),
        str(feed.link),
        str(categories),
        str(feed.language) || 'en',
        feed.explicit ? '1' : '0',
        str(feed.itunesType) || 'episodic',
    ];
}

/**
 * Stage 3: values for the latest-episode UPDATE, in the statement's SET order
 * (latest_ep_id … medium, last_ep_sync), followed by the show ID.
 */
function tipUpdateArgs(latest, medium, nowMs, podId) {
    return [
        episodeIdText(latest.id),
        str(latest.title),
        asNonNegInt(latest.datePublished, 0),
        asNonNegInt(latest.duration, 0),
        str(latest.enclosureUrl),
        str(latest.image) || str(latest.feedImage),
        str(latest.enclosureType) || 'audio/mpeg',
        text.cleanDescription(latest.description),
        orNull(latest.chaptersUrl),
        orNull(latest.transcriptUrl),
        jsonOrNull(latest.persons),
        jsonOrNull(latest.transcripts),
        str(medium) || 'podcast',
        nowMs,
        podId,
    ];
}

module.exports = { apiImportArgs, tipUpdateArgs, categoryList };
