package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.network.session.ApiErrorCodes

/** How the API answered this phone lending its sign-in to a watch or a TV (`/auth/watch/code`, `/auth/tv/approve`). */
internal object LendingRules {
    /**
     * The phone has to pass boxlore's check before it can lend: its limited session was asked to
     * step up (401 `verification_required`: the check was skipped, failed, or can't run with boxlore
     * in the background) or refused (401 `attestation_required`), or the API wants a better-attested
     * phone than the limited session it got (403 `attested_session_required`, not [sentAttested]).
     */
    fun needsCheck(status: Int, error: String?, sentAttested: Boolean): Boolean =
        when (status) {
            UNAUTHORIZED -> error == ApiErrorCodes.VERIFICATION_REQUIRED || error == ApiErrorCodes.ATTESTATION_REQUIRED
            FORBIDDEN -> error == ApiErrorCodes.ATTESTED_SESSION_REQUIRED && !sentAttested
            else -> false
        }

    /**
     * The phone passed its check ([sentAttested]: a challenge session), yet the API wants App Check
     * before it lends (403 `attested_session_required`): no check on screen helps, so this phone
     * can't lend its sign-in for now.
     */
    fun cannotLend(status: Int, error: String?, sentAttested: Boolean): Boolean = status == FORBIDDEN && error == ApiErrorCodes.ATTESTED_SESSION_REQUIRED && sentAttested

    /** Lent too often lately (429 `too_many_attempts`: 10 a minute per account, a few a day from a phone without App Check). */
    fun tooMany(status: Int): Boolean = status == TOO_MANY

    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val TOO_MANY = 429
}
