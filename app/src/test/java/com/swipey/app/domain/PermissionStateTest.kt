package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateTest {
    @Test fun bothMediaPermissionsGrantedIsFull() =
        assertEquals(MediaAccess.FULL, resolveMediaAccess(true, true, false))

    @Test fun fullEvenWhenUserSelectedAlsoGranted() =
        assertEquals(MediaAccess.FULL, resolveMediaAccess(true, true, true))

    @Test fun userSelectedOnlyIsPartial() =
        assertEquals(MediaAccess.PARTIAL, resolveMediaAccess(false, false, true))

    @Test fun nothingGrantedIsDenied() =
        assertEquals(MediaAccess.DENIED, resolveMediaAccess(false, false, false))

    /** Images without video is not full access — the deck must show both types. */
    @Test fun imagesWithoutVideoIsNotFull() =
        assertEquals(MediaAccess.DENIED, resolveMediaAccess(true, false, false))

    @Test fun imagesWithoutVideoButUserSelectedIsPartial() =
        assertEquals(MediaAccess.PARTIAL, resolveMediaAccess(true, false, true))
}
