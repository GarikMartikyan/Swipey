package com.swipey.app.domain

/**
 * How long ago something was, bucketed for a human rather than measured for a machine.
 *
 * [count] is meaningful only for [DAYS], [WEEKS], [MONTHS] and [YEARS]; the other four name
 * themselves and carry a count of zero. Splitting "which phrase" from "what number" is what
 * keeps the wording out of the domain — the strings live in `Copy`, which is where anything
 * a user reads belongs.
 */
enum class AgeBucket { TODAY, YESTERDAY, DAYS, LAST_WEEK, WEEKS, MONTHS, LAST_YEAR, YEARS }

data class RelativeAge(val bucket: AgeBucket, val count: Int = 0)

/**
 * Buckets a whole number of days into the phrase that should describe it.
 *
 * The boundaries are chosen so that no bucket ever produces a degenerate phrase. "1 day ago"
 * is never reachable because 1 is [AgeBucket.YESTERDAY]; "1 week ago" is never reachable
 * because the first fortnight is [AgeBucket.LAST_WEEK]; "1 year ago" is never reachable
 * because [AgeBucket.LAST_YEAR] absorbs everything that rounds to one. Each of those would
 * have been a phrase a person would not say, and each is the kind of thing that only shows
 * up on the one day of the year it is wrong.
 *
 * Rounding rather than truncating inside the week and month buckets: 13 days is much closer
 * to two weeks than to one, and a user comparing two photographs cares about which is older,
 * not about which side of a floor they fall.
 *
 * A negative [daysAgo] — a file dated in the future, which a wrong device clock or a bad
 * EXIF import both produce — is reported as [AgeBucket.TODAY] rather than as a negative
 * count. "In 3 days" about a photograph already in the gallery is worse than a small lie.
 */
fun relativeAge(daysAgo: Int): RelativeAge = when {
    daysAgo <= 0 -> RelativeAge(AgeBucket.TODAY)
    daysAgo == 1 -> RelativeAge(AgeBucket.YESTERDAY)
    daysAgo < 7 -> RelativeAge(AgeBucket.DAYS, daysAgo)
    daysAgo < 14 -> RelativeAge(AgeBucket.LAST_WEEK)
    daysAgo < 60 -> RelativeAge(AgeBucket.WEEKS, Math.round(daysAgo / 7f))
    daysAgo < 365 -> RelativeAge(AgeBucket.MONTHS, Math.round(daysAgo / 30.4f))
    else -> {
        val years = Math.round(daysAgo / 365.25f)
        if (years <= 1) RelativeAge(AgeBucket.LAST_YEAR) else RelativeAge(AgeBucket.YEARS, years)
    }
}
