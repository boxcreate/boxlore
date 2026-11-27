package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.network.session.ApiErrorCodes

/** How the API answered this phone lending its sign-in to a watch or a TV (`/auth/watch/code`, `/auth/tv/approve`). */
internal object LendingRules {
    /**
     * The phone has to pass boxlore's check before it can lend: its limited session was asked to
     * step up (401 `verification_required`: the check was skipped, failed, or can't run with boxlore
     * in the background) or refused (401 `attestation_required`), or the API wants a better-attested
     * phone (403 `attested_session_required`).
     */
    fun needsCheck(status: Int, error: String?): Boolean =
        when (status) {
            UNAUTHORIZED -> error == ApiErrorCodes.VERIFICATION_REQUIRED || error == ApiErrorCodes.ATTESTATION_REQUIRED
            FORBIDDEN -> error == ApiErrorCodes.ATTESTED_SESSION_REQUIRED
            else -> false
        }

    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
}
