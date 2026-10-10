# Announcement composer

## Purpose

The static composer creates legacy-compatible FCM announcement drafts, previews the native app's visual design, and requires explicit review before a workflow request. The existing Google/password/TOTP gateway and its encrypted configuration remain local and unchanged. The composer never stores or reads sender credentials beyond the gateway's existing in-memory session.

## Behavior

- Android renders compact and full-screen announcements in native Compose. The browser preview mirrors their Material 3 surfaces, typography, spacing and actions, using the same Google Sans font. Preview dimensions are content dp excluding system bars; selected Classic colors and text scale match that profile. The surrounding app page is omitted. The preview frame has its own scoped styling and explicit dimensions, so legacy gateway phone styles cannot crop its content. Actual custom themes, system bars and system notifications are device-owned and cannot be represented as a universal screenshot.
- Accent roles, banner/uncropped cover, Markdown headings, callouts, bullets and emphasis are controlled presentation options. Raw HTML is text. The native app formats text and invokes its already-parsed actions; it has no embedded browser or JavaScript bridge.
- Drafts default to isolated test builds and provider validation. Test mode rejects production audiences. A reviewed draft is snapshotted, and edits invalidate approval. Its SHA-256 digest is checked again by the workflow before real delivery. Workflow acceptance is not device delivery confirmation.
- In-app release alerts always have Download update, View on GitHub and Dismiss. Download enters the native updater; View on GitHub preserves the alert on return. Content, Appearance and Delivery are separate editor tabs, and the final review shows the complete selected viewport and delivery details. Narrow screens open at the preview and scroll to approval; notification-only delivery shows its exact text instead of an in-app alert. Android owns the system notification layout. Ordinary announcements can reach both install channels. GitHub release announcements normally target direct installs, with an explicit manual include-Play override. Release delivery uses the new direct_users audience, excluding Play/test topics; older clients on prod_users are not classifiable and are not included until updated. Automatic publication produces a review artifact, never sends automatically; import that artifact into the composer to edit, review and approve.
- Saved drafts are local browser data. Existing gateway configuration, tokens, backups and hosting configuration remain ignored; deploying this panel is separate from pushing Android changes.

## Development and verification

Edit browser presentation assets here when the native visual specification changes. Run `python3 .github/scripts/sync_announcement_preview.py` after app-font changes; `--check` checks the font only. Verify style and layout against phone screenshots for compact/full-screen, light/dark and enlarged text. Browser previews exclude Android system bars and cannot promise identical rendering across devices. Serve this directory locally and open `preview.html` for an unauthenticated offline composer; without a gateway session it cannot dispatch. A `preview.html` screen never grants sender access.

Run `node --test .github/scripts/test_announcement_admin.cjs` and the Python release-tooling suite. Test preview/digest parity, edited approval, UTF-8 bounds, test audience rejection and both large-text layouts. Native JVM tests cover persistence, legacy fallback, audience/version filtering and process/permission return. Device acceptance is still required for Android permission and installation prompts.
