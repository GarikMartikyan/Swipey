package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeAgeTest {

    @Test fun todayAndYesterdayNameThemselves() {
        assertEquals(RelativeAge(AgeBucket.TODAY), relativeAge(0))
        assertEquals(RelativeAge(AgeBucket.YESTERDAY), relativeAge(1))
    }

    @Test fun theRestOfTheWeekCountsInDays() {
        assertEquals(RelativeAge(AgeBucket.DAYS, 2), relativeAge(2))
        assertEquals(RelativeAge(AgeBucket.DAYS, 6), relativeAge(6))
    }

    @Test fun theSecondWeekIsLastWeek() {
        assertEquals(RelativeAge(AgeBucket.LAST_WEEK), relativeAge(7))
        assertEquals(RelativeAge(AgeBucket.LAST_WEEK), relativeAge(13))
    }

    /**
     * The three phrases a person would never say. Each is reachable only by an off-by-one at
     * a bucket boundary, and each reads as a bug to the user rather than as a date.
     */
    @Test fun neverSaysOneOfAnything() {
        for (days in 0..4000) {
            val age = relativeAge(days)
            val degenerate = age.count == 1 &&
                (age.bucket == AgeBucket.DAYS || age.bucket == AgeBucket.WEEKS ||
                    age.bucket == AgeBucket.MONTHS || age.bucket == AgeBucket.YEARS)
            assertEquals("day $days produced a count of 1 in ${age.bucket}", false, degenerate)
        }
    }

    @Test fun weeksRoundRatherThanTruncate() {
        // 17 days is 2.43 weeks, 18 is 2.57 — the nearest whole week flips between them.
        assertEquals(RelativeAge(AgeBucket.WEEKS, 2), relativeAge(17))
        assertEquals(RelativeAge(AgeBucket.WEEKS, 3), relativeAge(18))
    }

    @Test fun weeksRunToTheMonthBoundary() {
        assertEquals(AgeBucket.WEEKS, relativeAge(59).bucket)
        assertEquals(AgeBucket.MONTHS, relativeAge(60).bucket)
    }

    @Test fun monthsRunToTheYearBoundary() {
        assertEquals(RelativeAge(AgeBucket.MONTHS, 2), relativeAge(60))
        assertEquals(AgeBucket.MONTHS, relativeAge(364).bucket)
        assertEquals(AgeBucket.LAST_YEAR, relativeAge(365).bucket)
    }

    @Test fun anythingRoundingToOneYearIsLastYear() {
        assertEquals(RelativeAge(AgeBucket.LAST_YEAR), relativeAge(365))
        assertEquals(RelativeAge(AgeBucket.LAST_YEAR), relativeAge(500))
    }

    @Test fun beyondThatItCountsYears() {
        assertEquals(RelativeAge(AgeBucket.YEARS, 2), relativeAge(730))
        assertEquals(RelativeAge(AgeBucket.YEARS, 5), relativeAge(1826))
    }

    /** A future timestamp — a wrong clock, or a bad import — must not read as "in 3 days". */
    @Test fun futureDatesReadAsToday() {
        assertEquals(RelativeAge(AgeBucket.TODAY), relativeAge(-1))
        assertEquals(RelativeAge(AgeBucket.TODAY), relativeAge(-900))
    }

    @Test fun bucketsNeverGoBackwardsAsDaysIncrease() {
        var previous = -1
        for (days in 0..4000) {
            val ordinal = relativeAge(days).bucket.ordinal
            assertEquals("bucket went backwards at day $days", true, ordinal >= previous)
            previous = ordinal
        }
    }
}
