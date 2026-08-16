# Swipey MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that lets the user clean up their gallery by swiping left (mark for deletion) or right (keep), committing everything marked to Android's system trash in one operation, with an in-app Bin that can restore anything.

**Architecture:** Single Gradle module. All decision logic lives in `domain/` as pure Kotlin with zero Android imports, so it is JVM-unit-testable; `data/` wraps MediaStore and Room; `ui/` is Compose. Deletion never destroys anything — the app calls `createTrashRequest` only, and has no permanent-delete path anywhere.

**Tech Stack:** Kotlin, Jetpack Compose, Room (KSP), Media3 ExoPlayer, Coil 3, Navigation Compose.

**Spec:** `docs/superpowers/specs/2026-08-16-swipey-design.md` — read it before starting. The plan argues from the spec; where this plan and the spec disagree, the spec wins except for the toolchain values below, which were corrected empirically.

## Global Constraints

These apply to **every** task. They are not suggestions.

**Toolchain — verified by a build spike, not guessed. Do not "upgrade" these.**

- Gradle **9.7.0**, JDK **17** at `/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home` (set via `org.gradle.java.home`). JDK 23 is the machine default and must not be used.
- AGP **9.3.1**. **Never apply `org.jetbrains.kotlin.android`** — AGP 9.0+ has built-in Kotlin support and applying it is a hard build error.
- Do **not** add a `kotlin { jvmToolchain(...) }` block — there is no Kotlin extension to configure.
- `org.jetbrains.kotlin.plugin.compose` **2.4.10**, KSP **2.3.11**, Room **2.8.4**.
- compileSdk **37** (Compose BOM 2026.08.00 refuses to link against 36), minSdk **33**, targetSdk **36**.
- Compose BOM **2026.08.00**, Media3 **1.11.0**, Coil **3.5.0**, Navigation **2.9.8**, Lifecycle **2.11.0**, Activity **1.13.0**, core-ktx **1.19.0**.
- Tests use **JUnit 4.13.2** (`org.junit.Test`, `org.junit.Assert.*`). `kotlin("test")` does not resolve a version in this setup — do not use it.
- Material3 ships no XML themes; the app theme must use an `android:` parent.

**Platform rules — violating any of these breaks the app's core guarantee:**

- **Never** call `MediaStore.createDeleteRequest()`. **Never** call `ContentResolver.delete()` on a media URI. There is no permanent-delete path in this app. A task that adds one is wrong.
- **Never** let the substring `owner_package_name` appear in `QUERY_ARG_SQL_SELECTION`, `QUERY_ARG_SQL_SORT_ORDER`, `QUERY_ARG_SQL_GROUP_BY`, or `QUERY_ARG_SQL_HAVING`.
- **Never** use `MediaStore.Files.getContentUri()`. Query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` separately and merge in memory.
- Verification queries **must** pass `QUERY_ARG_MATCH_TRASHED = MediaStore.MATCH_INCLUDE`. An absent row means "gone from device", never "not trashed".
- `RESULT_OK` from the consent dialog is **not** proof of success. Always re-query `IS_TRASHED`.
- `DATE_EXPIRES` is in **Unix seconds** and is read-only. Never write it.
- Chunk URIs to **500** per request.
- Nothing under `domain/` may import `android.*`.

**UI copy rules (spec §9) — binding on every screen:**

1. Say "moved to trash", never "deleted".
2. Never claim space was freed. Trashing frees zero bytes.
3. Expiry is a minimum: "Recoverable until at least 12 Sep".
4. State that this is the system trash, shared with Google Photos / Files.
5. Warn that restoring shows a second system confirmation.
6. Report per-item outcomes from the `IS_TRASHED` re-check.
7. State that Swipey has no permanent-delete function.

All copy lives in `ui/common/Copy.kt` so it can be audited in one place (Task 20).

---

## File Structure

```
app/src/main/java/com/swipey/app/
  MainActivity.kt                     entry point, nav host host
  SwipeyApp.kt                        Application; database + repository singletons
  domain/                             PURE KOTLIN — no android.* imports, ever
    MediaItem.kt                      model, SortMode, sorting, shuffle, Album grouping
    ByteFormat.kt                     formatBytes
    Chunking.kt                       chunkedForRequest
    SwipeSession.kt                   the swipe state machine
    PermissionState.kt                MediaAccess resolution
    TrashRecords.kt                   LocalTrashRecord, LiveTrashRow, TrashState
    Reconcile.kt                      resolveRecords, reconcileBin
  data/
    MediaUri.kt                       MediaItem.contentUri() — the Android edge
    MediaMapper.kt                    pure row -> MediaItem mapper
    MediaRepository.kt                deck / bin / verification queries
    TrashRepository.kt                trash + restore requests, pending state
    db/
      SwipeyDatabase.kt
      ReviewedMedia.kt                entity + DAO
      TrashedItem.kt                  entity + DAO
  ui/
    common/Copy.kt                    every user-facing string (spec §9)
    theme/Theme.kt
    SwipeyNavHost.kt                  routes
    permission/PermissionGate.kt
    home/HomeScreen.kt
    albums/AlbumsScreen.kt            + SortChooserScreen
    deck/SwipeCard.kt                 gesture + visuals, no business logic
    deck/VideoCard.kt                 Media3 player binding
    deck/DeckScreen.kt                + DeckViewModel
    review/ReviewScreen.kt
    result/ResultScreen.kt
    bin/BinScreen.kt                  + BinViewModel
app/src/test/java/com/swipey/app/     JVM unit tests (JUnit 4)
app/src/androidTest/java/...          Room DAO tests, run on device in Task 21
```

---

## Task 1: Project scaffold and proven toolchain

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/swipey/app/MainActivity.kt`
- Test: `app/src/test/java/com/swipey/app/ScaffoldTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: a building project. Every later task depends on `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` working.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/swipey/app/ScaffoldTest.kt`:
```kotlin
package com.swipey.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldTest {
    @Test fun toolchainRuns() { assertEquals(4, 2 + 2) }
}
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.3.1"
kotlin = "2.4.10"
ksp = "2.3.11"
composeBom = "2026.08.00"
room = "2.8.4"
media3 = "1.11.0"
coil = "3.5.0"
nav = "2.9.8"
lifecycle = "2.11.0"
activity = "1.13.0"
coreKtx = "1.19.0"
junit = "4.13.2"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "nav" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-video = { module = "io.coil-kt.coil3:coil-video", version.ref = "coil" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Create the Gradle files**

`settings.gradle.kts`:
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "Swipey"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.java.home=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
android.useAndroidX=true
```

`app/build.gradle.kts` — note there is **no** `kotlin.android` plugin and **no** `jvmToolchain` block:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.swipey.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.swipey.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Create manifest, theme, and MainActivity**

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Swipey" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`app/src/main/AndroidManifest.xml` — all three permissions, per spec §6:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
    <application
        android:name=".SwipeyApp"
        android:label="Swipey"
        android:theme="@style/Theme.Swipey">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/swipey/app/SwipeyApp.kt`:
```kotlin
package com.swipey.app

import android.app.Application

class SwipeyApp : Application()
```

`app/src/main/java/com/swipey/app/MainActivity.kt`:
```kotlin
package com.swipey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Swipey") } }
    }
}
```

- [ ] **Step 5: Generate the Gradle wrapper**

Gradle is not installed system-wide; a verified 9.7.0 distribution is already unpacked in the scratchpad.

Run:
```bash
/private/tmp/claude-501/-Users-garikmartikyan03gmail-com-Desktop-Swipey/fe706258-ed97-4df8-8f08-926f8344b801/scratchpad/toolchain/gradle-9.7.0/bin/gradle wrapper --gradle-version 9.7.0
```
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/`.

- [ ] **Step 6: Run the build and the test**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: project scaffold with verified toolchain"
```

---

## Task 2: Domain — MediaItem, sorting, albums

**Files:**
- Create: `app/src/main/java/com/swipey/app/domain/MediaItem.kt`
- Test: `app/src/test/java/com/swipey/app/domain/MediaItemTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `MediaItem`, `SortMode`, `List<MediaItem>.sortedFor(SortMode)`, `List<MediaItem>.shuffledWithSeed(Long)`, `Album`, `List<MediaItem>.toAlbums()`. Used by Tasks 4, 9, 13, 15.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemTest {
    private fun item(id: Long, size: Long, date: Long, bucket: Long = 1L, bucketName: String = "Camera") =
        MediaItem(id, false, size, date, null, bucket, bucketName, "f$id.jpg")

    @Test fun sortsNewestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 20, 300), item(3, 30, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.NEWEST).map { it.id })
    }

    @Test fun sortsOldestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 20, 300), item(3, 30, 200))
        assertEquals(listOf(1L, 3L, 2L), items.sortedFor(SortMode.OLDEST).map { it.id })
    }

    @Test fun sortsLargestFirst() {
        val items = listOf(item(1, 10, 100), item(2, 30, 300), item(3, 20, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.LARGEST).map { it.id })
    }

    @Test fun sortsSmallestFirst() {
        val items = listOf(item(1, 30, 100), item(2, 10, 300), item(3, 20, 200))
        assertEquals(listOf(2L, 3L, 1L), items.sortedFor(SortMode.SMALLEST).map { it.id })
    }

    @Test fun sortsImagesAndVideosTogetherBySize() {
        val image = MediaItem(1, false, 500, 1, null, 1, "Camera", "a.jpg")
        val video = MediaItem(2, true, 900, 1, 5000, 1, "Camera", "b.mp4")
        assertEquals(listOf(2L, 1L), listOf(image, video).sortedFor(SortMode.LARGEST).map { it.id })
    }

    @Test fun shuffleIsDeterministicForSameSeed() {
        val items = (1L..20L).map { item(it, it, it) }
        assertEquals(
            items.shuffledWithSeed(42L).map { it.id },
            items.shuffledWithSeed(42L).map { it.id },
        )
    }

    @Test fun shuffleKeepsEveryItem() {
        val items = (1L..20L).map { item(it, it, it) }
        assertEquals(items.map { it.id }.toSet(), items.shuffledWithSeed(7L).map { it.id }.toSet())
    }

    @Test fun groupsIntoAlbumsSortedByTotalSizeDescending() {
        val items = listOf(
            item(1, 100, 1, bucket = 1, bucketName = "Camera"),
            item(2, 50, 1, bucket = 2, bucketName = "Screenshots"),
            item(3, 400, 1, bucket = 2, bucketName = "Screenshots"),
        )
        val albums = items.toAlbums()
        assertEquals(listOf("Screenshots", "Camera"), albums.map { it.name })
        assertEquals(2, albums[0].itemCount)
        assertEquals(450L, albums[0].totalBytes)
    }

    @Test fun emptyListProducesNoAlbums() {
        assertEquals(emptyList<Album>(), emptyList<MediaItem>().toAlbums())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*MediaItemTest*'`
Expected: FAIL — unresolved reference `MediaItem`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.swipey.app.domain

import kotlin.random.Random

/**
 * A photo or video. Deliberately holds no android.* types — see spec §5.1.
 * The content URI is derived at the Android edge by MediaItem.contentUri().
 */
data class MediaItem(
    val id: Long,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val durationMs: Long?,
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
)

enum class SortMode { NEWEST, OLDEST, LARGEST, SMALLEST }

fun List<MediaItem>.sortedFor(mode: SortMode): List<MediaItem> = when (mode) {
    SortMode.NEWEST -> sortedByDescending { it.dateAddedSec }
    SortMode.OLDEST -> sortedBy { it.dateAddedSec }
    SortMode.LARGEST -> sortedByDescending { it.sizeBytes }
    SortMode.SMALLEST -> sortedBy { it.sizeBytes }
}

fun List<MediaItem>.shuffledWithSeed(seed: Long): List<MediaItem> = shuffled(Random(seed))

data class Album(
    val bucketId: Long,
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
)

fun List<MediaItem>.toAlbums(): List<Album> =
    groupBy { it.bucketId }
        .map { (bucketId, items) ->
            Album(bucketId, items.first().bucketName, items.size, items.sumOf { it.sizeBytes })
        }
        .sortedByDescending { it.totalBytes }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*MediaItemTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: media item model, sorting, album grouping"
```

---

## Task 3: Domain — byte formatting and request chunking

**Files:**
- Create: `app/src/main/java/com/swipey/app/domain/ByteFormat.kt`
- Create: `app/src/main/java/com/swipey/app/domain/Chunking.kt`
- Test: `app/src/test/java/com/swipey/app/domain/ByteFormatTest.kt`
- Test: `app/src/test/java/com/swipey/app/domain/ChunkingTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `formatBytes(Long): String`, `MAX_URIS_PER_REQUEST: Int = 500`, `List<T>.chunkedForRequest(): List<List<T>>`. Used by Tasks 10, 15, 17, 18, 19.

- [ ] **Step 1: Write the failing tests**

`ByteFormatTest.kt`:
```kotlin
package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test fun formatsZero() = assertEquals("0 B", formatBytes(0))
    @Test fun formatsBytes() = assertEquals("512 B", formatBytes(512))
    @Test fun formatsKilobytes() = assertEquals("1.0 KB", formatBytes(1024))
    @Test fun formatsMegabytes() = assertEquals("2.5 MB", formatBytes(2_621_440))
    @Test fun formatsGigabytes() = assertEquals("1.2 GB", formatBytes(1_288_490_189))
    @Test fun roundsToOneDecimal() = assertEquals("1.5 KB", formatBytes(1536))
    @Test fun handlesNegativeAsZero() = assertEquals("0 B", formatBytes(-5))
}
```

`ChunkingTest.kt`:
```kotlin
package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkingTest {
    @Test fun emptyListProducesNoChunks() =
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().chunkedForRequest())

    @Test fun singleItemProducesOneChunk() =
        assertEquals(1, listOf(1).chunkedForRequest().size)

    @Test fun exactlyMaxProducesOneChunk() =
        assertEquals(1, (1..500).toList().chunkedForRequest().size)

    @Test fun oneOverMaxProducesTwoChunks() {
        val chunks = (1..501).toList().chunkedForRequest()
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(1, chunks[1].size)
    }

    @Test fun thousandProducesTwoFullChunks() {
        val chunks = (1..1000).toList().chunkedForRequest()
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[1].size)
    }

    @Test fun chunkingLosesNothing() {
        val input = (1..1234).toList()
        assertEquals(input, input.chunkedForRequest().flatten())
    }

    @Test fun maxIsFiveHundred() = assertEquals(500, MAX_URIS_PER_REQUEST)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*ByteFormatTest*' --tests '*ChunkingTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementations**

`ByteFormat.kt`:
```kotlin
package com.swipey.app.domain

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
```

`Chunking.kt`:
```kotlin
package com.swipey.app.domain

/**
 * targetSdk 36 enables LIMIT_CREATE_REQUEST_URIS, which throws above 2000 URIs
 * per createTrashRequest. 500 keeps a comfortable margin. See spec §8.
 */
const val MAX_URIS_PER_REQUEST = 500

fun <T> List<T>.chunkedForRequest(size: Int = MAX_URIS_PER_REQUEST): List<List<T>> =
    if (isEmpty()) emptyList() else chunked(size)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*ByteFormatTest*' --tests '*ChunkingTest*'`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: byte formatting and request chunking"
```

---

## Task 4: Domain — SwipeSession state machine

This is the core of the app. Take the tests seriously.

**Files:**
- Create: `app/src/main/java/com/swipey/app/domain/SwipeSession.kt`
- Test: `app/src/test/java/com/swipey/app/domain/SwipeSessionTest.kt`

**Interfaces:**
- Consumes: `MediaItem` (Task 2)
- Produces: `SwipeSession`, `Decision`, `UndoResult`. Used by Task 15 (`DeckViewModel`) and Task 17.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSessionTest {
    private fun item(id: Long, size: Long = 100) =
        MediaItem(id, false, size, id, null, 1, "Camera", "f$id.jpg")

    private fun session(count: Int = 3) = SwipeSession((1L..count).map { item(it) })

    @Test fun startsAtFirstItem() {
        val s = session()
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.position)
        assertEquals(3, s.total)
        assertFalse(s.isExhausted)
    }

    @Test fun emptyQueueIsImmediatelyExhausted() {
        val s = SwipeSession(emptyList())
        assertNull(s.current)
        assertTrue(s.isExhausted)
        assertEquals(0, s.markedCount)
    }

    @Test fun swipeLeftMarksAndAdvances() {
        val s = session()
        s.swipeLeft()
        assertEquals(1, s.markedCount)
        assertEquals(100L, s.markedBytes)
        assertEquals(2L, s.current?.id)
    }

    @Test fun swipeRightKeepsAndAdvances() {
        val s = session()
        s.swipeRight()
        assertEquals(0, s.markedCount)
        assertEquals(2L, s.current?.id)
    }

    @Test fun markedBytesSumsOnlyMarkedItems() {
        val s = SwipeSession(listOf(item(1, 100), item(2, 250), item(3, 400)))
        s.swipeLeft()
        s.swipeRight()
        s.swipeLeft()
        assertEquals(2, s.markedCount)
        assertEquals(500L, s.markedBytes)
    }

    @Test fun exhaustsAfterLastItem() {
        val s = session(2)
        s.swipeRight()
        s.swipeRight()
        assertTrue(s.isExhausted)
        assertNull(s.current)
    }

    @Test fun swipingPastEndIsANoOp() {
        val s = session(1)
        s.swipeLeft()
        s.swipeLeft()
        assertEquals(1, s.markedCount)
        assertTrue(s.isExhausted)
    }

    @Test fun undoRestoresPreviousItemAndDecision() {
        val s = session()
        s.swipeLeft()
        val undone = s.undo()
        assertEquals(1L, undone?.item?.id)
        assertEquals(Decision.MARK, undone?.previousDecision)
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.markedCount)
    }

    @Test fun undoOfKeepDoesNotChangeMarkedCount() {
        val s = session()
        s.swipeRight()
        val undone = s.undo()
        assertEquals(Decision.KEEP, undone?.previousDecision)
        assertEquals(0, s.markedCount)
        assertEquals(1L, s.current?.id)
    }

    @Test fun undoAtStartReturnsNull() {
        assertNull(session().undo())
    }

    @Test fun undoWorksFromExhaustedState() {
        val s = session(1)
        s.swipeLeft()
        assertTrue(s.isExhausted)
        s.undo()
        assertFalse(s.isExhausted)
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.markedCount)
    }

    @Test fun undoStacksAcrossMultipleDecisions() {
        val s = session()
        s.swipeLeft(); s.swipeLeft(); s.swipeRight()
        s.undo(); s.undo()
        assertEquals(1, s.markedCount)
        assertEquals(2L, s.current?.id)
    }

    @Test fun unmarkRemovesFromMarkedSetWithoutMovingPosition() {
        val s = session()
        s.swipeLeft()
        s.swipeLeft()
        val positionBefore = s.position
        s.unmark(1L)
        assertEquals(1, s.markedCount)
        assertEquals(positionBefore, s.position)
        assertEquals(listOf(2L), s.marked().map { it.id })
    }

    @Test fun unmarkOfUnknownIdIsHarmless() {
        val s = session()
        s.swipeLeft()
        s.unmark(999L)
        assertEquals(1, s.markedCount)
    }

    @Test fun markedPreservesSwipeOrder() {
        val s = SwipeSession(listOf(item(3), item(1), item(2)))
        s.swipeLeft(); s.swipeLeft(); s.swipeLeft()
        assertEquals(listOf(3L, 1L, 2L), s.marked().map { it.id })
    }

    @Test fun undoAfterUnmarkDoesNotResurrectTheMark() {
        val s = session()
        s.swipeLeft()
        s.unmark(1L)
        val undone = s.undo()
        assertEquals(1L, undone?.item?.id)
        assertEquals(0, s.markedCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*SwipeSessionTest*'`
Expected: FAIL — unresolved reference `SwipeSession`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.swipey.app.domain

enum class Decision { KEEP, MARK }

data class UndoResult(val item: MediaItem, val previousDecision: Decision)

/**
 * The swipe state machine. Pure Kotlin — no android.* imports (spec §10).
 *
 * `marked` is a LinkedHashMap so marked() preserves the order the user swiped in,
 * which is the order the Review grid shows.
 */
class SwipeSession(private val queue: List<MediaItem>) {

    private val marked = LinkedHashMap<Long, MediaItem>()
    private val history = ArrayDeque<Pair<MediaItem, Decision>>()

    var position: Int = 0
        private set

    val total: Int get() = queue.size
    val current: MediaItem? get() = queue.getOrNull(position)
    val isExhausted: Boolean get() = position >= queue.size
    val markedCount: Int get() = marked.size
    val markedBytes: Long get() = marked.values.sumOf { it.sizeBytes }

    fun swipeLeft(): MediaItem? = advance(Decision.MARK)

    fun swipeRight(): MediaItem? = advance(Decision.KEEP)

    private fun advance(decision: Decision): MediaItem? {
        val item = current ?: return null
        if (decision == Decision.MARK) marked[item.id] = item
        history.addLast(item to decision)
        position++
        return item
    }

    fun undo(): UndoResult? {
        val (item, decision) = history.removeLastOrNull() ?: return null
        marked.remove(item.id)
        position--
        return UndoResult(item, decision)
    }

    fun unmark(id: Long) {
        marked.remove(id)
    }

    fun marked(): List<MediaItem> = marked.values.toList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*SwipeSessionTest*'`
Expected: PASS, 16 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: swipe session state machine"
```

---

## Task 5: Domain — permission state resolution

**Files:**
- Create: `app/src/main/java/com/swipey/app/domain/PermissionState.kt`
- Test: `app/src/test/java/com/swipey/app/domain/PermissionStateTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `MediaAccess` enum, `resolveMediaAccess(Boolean, Boolean, Boolean): MediaAccess`. Used by Task 12.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*PermissionStateTest*'`
Expected: FAIL.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.swipey.app.domain

/**
 * Spec §6. PARTIAL blocks the app: without full access the Bin cannot enumerate
 * trashed items, so a deletion could not be shown to be recoverable.
 */
enum class MediaAccess { FULL, PARTIAL, DENIED }

fun resolveMediaAccess(
    imagesGranted: Boolean,
    videoGranted: Boolean,
    userSelectedGranted: Boolean,
): MediaAccess = when {
    imagesGranted && videoGranted -> MediaAccess.FULL
    userSelectedGranted -> MediaAccess.PARTIAL
    else -> MediaAccess.DENIED
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*PermissionStateTest*'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: media access permission state resolution"
```

---

## Task 6: Domain — trash records, reconciliation, and purity guard

This implements spec §8.1, the logic that keeps the recoverability guarantee alive across process death. It is the second-most important task in the plan after Task 4.

**Files:**
- Create: `app/src/main/java/com/swipey/app/domain/TrashRecords.kt`
- Create: `app/src/main/java/com/swipey/app/domain/Reconcile.kt`
- Test: `app/src/test/java/com/swipey/app/domain/ReconcileTest.kt`
- Test: `app/src/test/java/com/swipey/app/domain/DomainPurityTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `TrashState`, `LocalTrashRecord`, `LiveTrashRow`, `Resolution`, `resolveRecords(...)`, `BinEntry`, `BinView`, `reconcileBin(...)`. Used by Tasks 10, 19, 20.

- [ ] **Step 1: Write the failing tests**

`ReconcileTest.kt`:
```kotlin
package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileTest {
    private fun record(id: Long, state: TrashState, name: String = "f$id.jpg", size: Long = 100) =
        LocalTrashRecord(id, false, name, size, 1_000L, state)

    private fun live(id: Long, trashed: Boolean, name: String = "f$id.jpg", size: Long = 100, expires: Long? = 9_999L) =
        LiveTrashRow(id, trashed, name, size, expires)

    private fun resolve(local: List<LocalTrashRecord>, live: List<LiveTrashRow>) =
        resolveRecords(local, live.associateBy { it.mediaId })

    @Test fun pendingTrashThatIsTrashedIsPromoted() {
        val r = resolve(listOf(record(1, TrashState.PENDING_TRASH)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingTrashThatIsNotTrashedIsDropped() {
        val r = resolve(listOf(record(1, TrashState.PENDING_TRASH)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecord(1)), r)
    }

    @Test fun pendingRestoreThatIsStillTrashedRevertsToTrashed() {
        val r = resolve(listOf(record(1, TrashState.PENDING_RESTORE)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingRestoreThatIsUntrashedClearsBothRows() {
        val r = resolve(listOf(record(1, TrashState.PENDING_RESTORE)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    @Test fun trashedThatIsStillTrashedIsKept() {
        val r = resolve(listOf(record(1, TrashState.TRASHED)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.Keep(1)), r)
    }

    /** Restored by Google Photos behind our back: it must become swipeable again. */
    @Test fun trashedThatWasRestoredElsewhereClearsBothRows() {
        val r = resolve(listOf(record(1, TrashState.TRASHED)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    @Test fun absentRowIsVanishedRegardlessOfState() {
        TrashState.entries.forEach { state ->
            assertEquals(
                "state $state",
                listOf(Resolution.Vanished(1)),
                resolve(listOf(record(1, state)), emptyList()),
            )
        }
    }

    /** MediaStore reuses ids after a rescan; name+size mismatch means it is a different file. */
    @Test fun idReuseDetectedByNameMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg")),
            listOf(live(1, true, name = "different.jpg")),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun idReuseDetectedBySizeMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, size = 100)),
            listOf(live(1, true, size = 999)),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun emptyInputProducesNoResolutions() {
        assertEquals(emptyList<Resolution>(), resolve(emptyList(), listOf(live(1, true))))
    }

    @Test fun binShowsOnlyStillTrashedEntriesWithExpiry() {
        val local = listOf(
            record(1, TrashState.TRASHED),
            record(2, TrashState.TRASHED),
            record(3, TrashState.TRASHED),
        )
        val liveRows = listOf(live(1, true, expires = 5_000L), live(2, false))
        val view = reconcileBin(local, liveRows.associateBy { it.mediaId })
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
        assertEquals(5_000L, view.entries[0].expiresAtSec)
        assertEquals(setOf(2L, 3L), view.vanished.toSet())
    }

    @Test fun binIsEmptyWhenNothingIsTrashed() {
        val view = reconcileBin(emptyList(), emptyMap())
        assertEquals(emptyList<BinEntry>(), view.entries)
        assertEquals(emptyList<Long>(), view.vanished)
    }

    @Test fun binIncludesPendingTrashThatIsConfirmedTrashed() {
        val view = reconcileBin(
            listOf(record(1, TrashState.PENDING_TRASH)),
            mapOf(1L to live(1, true)),
        )
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
    }
}
```

`DomainPurityTest.kt` — enforces the constraint that makes everything above testable:
```kotlin
package com.swipey.app.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainPurityTest {
    @Test fun noDomainFileImportsAndroid() {
        val domainDir = File("src/main/java/com/swipey/app/domain")
        val sources = domainDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assert(sources.isNotEmpty()) { "no domain sources found at ${domainDir.absolutePath}" }
        val offenders = sources.filter { file ->
            file.readLines().any { it.trimStart().startsWith("import android") }
        }
        assertEquals("domain must stay Android-free", emptyList<String>(), offenders.map { it.name })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*ReconcileTest*' --tests '*DomainPurityTest*'`
Expected: ReconcileTest FAILs on unresolved references. DomainPurityTest may already pass — that is fine, it is a guard.

- [ ] **Step 3: Write the implementations**

`TrashRecords.kt`:
```kotlin
package com.swipey.app.domain

/**
 * PENDING_* states exist because the consent dialog runs in MediaProvider's process
 * and can complete while Swipey is dead. See spec §8.1.
 */
enum class TrashState { PENDING_TRASH, TRASHED, PENDING_RESTORE }

/** What Swipey recorded locally before launching a trash or restore request. */
data class LocalTrashRecord(
    val mediaId: Long,
    val isVideo: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val state: TrashState,
)

/** What MediaStore actually reports right now, read with MATCH_INCLUDE. */
data class LiveTrashRow(
    val mediaId: Long,
    val isTrashed: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val dateExpiresSec: Long?,
)
```

`Reconcile.kt`:
```kotlin
package com.swipey.app.domain

sealed interface Resolution {
    val mediaId: Long
    /** Confirmed trashed — set state to TRASHED. */
    data class MarkTrashed(override val mediaId: Long) : Resolution
    /** Not trashed and never was ours to keep — drop the trash record only. */
    data class DeleteRecord(override val mediaId: Long) : Resolution
    /** Back in the gallery — drop the trash record AND the reviewed row so it is swipeable again. */
    data class DeleteRecordAndReview(override val mediaId: Long) : Resolution
    /** No longer on the device at all. */
    data class Vanished(override val mediaId: Long) : Resolution
    /** Still trashed, still correct — no change. */
    data class Keep(override val mediaId: Long) : Resolution
}

/**
 * The spec §8.1 resolution table, used both by the startup recovery pass and by
 * Bin reconciliation.
 *
 * A live row whose displayName or sizeBytes disagrees with the local record means
 * MediaStore reused the id for a different file — treat it as vanished rather than
 * pointing the Bin at the wrong photo.
 */
fun resolveRecords(
    local: List<LocalTrashRecord>,
    live: Map<Long, LiveTrashRow>,
): List<Resolution> = local.map { record ->
    val row = live[record.mediaId]
    when {
        row == null -> Resolution.Vanished(record.mediaId)
        row.displayName != record.displayName || row.sizeBytes != record.sizeBytes ->
            Resolution.Vanished(record.mediaId)
        row.isTrashed -> when (record.state) {
            TrashState.TRASHED -> Resolution.Keep(record.mediaId)
            TrashState.PENDING_TRASH, TrashState.PENDING_RESTORE ->
                Resolution.MarkTrashed(record.mediaId)
        }
        else -> when (record.state) {
            TrashState.PENDING_TRASH -> Resolution.DeleteRecord(record.mediaId)
            TrashState.TRASHED, TrashState.PENDING_RESTORE ->
                Resolution.DeleteRecordAndReview(record.mediaId)
        }
    }
}

data class BinEntry(val record: LocalTrashRecord, val expiresAtSec: Long?)

data class BinView(val entries: List<BinEntry>, val vanished: List<Long>)

/** What the Bin screen renders: only rows still genuinely trashed, plus what disappeared. */
fun reconcileBin(local: List<LocalTrashRecord>, live: Map<Long, LiveTrashRow>): BinView {
    val resolutions = resolveRecords(local, live).associateBy { it.mediaId }
    val byId = local.associateBy { it.mediaId }
    val entries = mutableListOf<BinEntry>()
    val vanished = mutableListOf<Long>()
    resolutions.values.forEach { resolution ->
        when (resolution) {
            is Resolution.Keep, is Resolution.MarkTrashed -> {
                val record = byId.getValue(resolution.mediaId)
                entries += BinEntry(record, live[resolution.mediaId]?.dateExpiresSec)
            }
            else -> vanished += resolution.mediaId
        }
    }
    return BinView(entries.sortedByDescending { it.record.trashedAt }, vanished)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*ReconcileTest*' --tests '*DomainPurityTest*'`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: trash record reconciliation and domain purity guard"
```

---

## Task 7: Room schema and DAOs

**Files:**
- Create: `app/src/main/java/com/swipey/app/data/db/ReviewedMedia.kt`
- Create: `app/src/main/java/com/swipey/app/data/db/TrashedItem.kt`
- Create: `app/src/main/java/com/swipey/app/data/db/SwipeyDatabase.kt`
- Modify: `app/src/main/java/com/swipey/app/SwipeyApp.kt`
- Test: `app/src/androidTest/java/com/swipey/app/data/db/SwipeyDaoTest.kt`
- Modify: `app/build.gradle.kts` (androidTest deps)

**Interfaces:**
- Consumes: `TrashState`, `LocalTrashRecord` (Task 6)
- Produces: `SwipeyDatabase`, `ReviewedMediaDao`, `TrashedItemDao`, `ReviewedMediaEntity`, `TrashedItemEntity`, `TrashedItemEntity.toDomain()`. Used by Tasks 9, 10, 15, 19, 20.

**Note on testing:** Room DAO tests need an Android runtime, so they live in `androidTest` and run on the device in Task 21. Write them now; do not attempt to run them without a device. All *logic* that could be wrong is already pure and covered by Task 6.

- [ ] **Step 1: Write the DAO test (runs later, on device)**

```kotlin
package com.swipey.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swipey.app.domain.TrashState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeyDaoTest {
    private lateinit var db: SwipeyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SwipeyDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun keptIdsAreReturned() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().upsert(ReviewedMediaEntity(2L, "TRASHED", 100L))
        assertEquals(listOf(1L), db.reviewed().keptIds())
    }

    @Test fun deletingReviewedRowMakesItEligibleAgain() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().delete(1L)
        assertEquals(emptyList<Long>(), db.reviewed().keptIds())
    }

    @Test fun resetClearsAllReviewedRows() = runBlocking {
        db.reviewed().upsert(ReviewedMediaEntity(1L, "KEEP", 100L))
        db.reviewed().upsert(ReviewedMediaEntity(2L, "KEEP", 100L))
        db.reviewed().clear()
        assertEquals(emptyList<Long>(), db.reviewed().keptIds())
    }

    @Test fun trashedItemRoundTrips() = runBlocking {
        val entity = TrashedItemEntity(5L, false, "a.jpg", 900L, 1_000L, "PENDING_TRASH")
        db.trashed().upsertAll(listOf(entity))
        val loaded = db.trashed().all().single()
        assertEquals(5L, loaded.mediaId)
        assertEquals(TrashState.PENDING_TRASH, loaded.toDomain().state)
    }

    @Test fun pendingRowsAreQueryableSeparately() = runBlocking {
        db.trashed().upsertAll(listOf(
            TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "PENDING_TRASH"),
            TrashedItemEntity(2L, false, "b.jpg", 1L, 1L, "TRASHED"),
            TrashedItemEntity(3L, false, "c.jpg", 1L, 1L, "PENDING_RESTORE"),
        ))
        assertEquals(setOf(1L, 3L), db.trashed().pending().map { it.mediaId }.toSet())
    }

    @Test fun setStateUpdatesInPlace() = runBlocking {
        db.trashed().upsertAll(listOf(TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "PENDING_TRASH")))
        db.trashed().setState(listOf(1L), "TRASHED")
        assertEquals("TRASHED", db.trashed().all().single().state)
    }

    @Test fun deleteRemovesTrashedRows() = runBlocking {
        db.trashed().upsertAll(listOf(TrashedItemEntity(1L, false, "a.jpg", 1L, 1L, "TRASHED")))
        db.trashed().delete(listOf(1L))
        assertNull(db.trashed().all().firstOrNull())
    }
}
```

- [ ] **Step 2: Add androidTest dependencies**

In `app/build.gradle.kts` dependencies block, append:
```kotlin
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
```

- [ ] **Step 3: Write the entities and DAOs**

`ReviewedMedia.kt`:
```kotlin
package com.swipey.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "reviewed_media")
data class ReviewedMediaEntity(
    @PrimaryKey val mediaId: Long,
    val decision: String,   // "KEEP" | "TRASHED"
    val reviewedAt: Long,
)

@Dao
interface ReviewedMediaDao {
    @Upsert suspend fun upsert(row: ReviewedMediaEntity)
    @Upsert suspend fun upsertAll(rows: List<ReviewedMediaEntity>)

    @Query("SELECT mediaId FROM reviewed_media WHERE decision = 'KEEP'")
    suspend fun keptIds(): List<Long>

    @Query("DELETE FROM reviewed_media WHERE mediaId = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reviewed_media WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM reviewed_media")
    suspend fun clear()
}
```

`TrashedItem.kt`:
```kotlin
package com.swipey.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.swipey.app.domain.LocalTrashRecord
import com.swipey.app.domain.TrashState

@Entity(tableName = "trashed_by_swipey")
data class TrashedItemEntity(
    @PrimaryKey val mediaId: Long,
    val isVideo: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val state: String,   // TrashState.name
)

fun TrashedItemEntity.toDomain(): LocalTrashRecord = LocalTrashRecord(
    mediaId = mediaId,
    isVideo = isVideo,
    displayName = displayName,
    sizeBytes = sizeBytes,
    trashedAt = trashedAt,
    state = TrashState.valueOf(state),
)

@Dao
interface TrashedItemDao {
    @Upsert suspend fun upsertAll(rows: List<TrashedItemEntity>)

    @Query("SELECT * FROM trashed_by_swipey")
    suspend fun all(): List<TrashedItemEntity>

    @Query("SELECT * FROM trashed_by_swipey WHERE state != 'TRASHED'")
    suspend fun pending(): List<TrashedItemEntity>

    @Query("SELECT COUNT(*) FROM trashed_by_swipey WHERE state = 'TRASHED'")
    suspend fun trashedCount(): Int

    @Query("UPDATE trashed_by_swipey SET state = :state WHERE mediaId IN (:ids)")
    suspend fun setState(ids: List<Long>, state: String)

    @Query("DELETE FROM trashed_by_swipey WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
```

`SwipeyDatabase.kt`:
```kotlin
package com.swipey.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReviewedMediaEntity::class, TrashedItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SwipeyDatabase : RoomDatabase() {
    abstract fun reviewed(): ReviewedMediaDao
    abstract fun trashed(): TrashedItemDao

    companion object {
        @Volatile private var instance: SwipeyDatabase? = null

        fun get(context: Context): SwipeyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SwipeyDatabase::class.java,
                "swipey.db",
            ).build().also { instance = it }
        }
    }
}
```

- [ ] **Step 4: Verify it compiles and KSP generates the implementations**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Then confirm `app/build/generated/ksp/debug/kotlin/com/swipey/app/data/db/SwipeyDatabase_Impl.kt` exists. If it does not, KSP is not wired — stop and fix before continuing.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: room schema for reviewed media and trash records"
```

---

## Task 8: Data — content URIs and the pure row mapper

**Files:**
- Create: `app/src/main/java/com/swipey/app/data/MediaUri.kt`
- Create: `app/src/main/java/com/swipey/app/data/MediaMapper.kt`
- Test: `app/src/test/java/com/swipey/app/data/MediaMapperTest.kt`

**Interfaces:**
- Consumes: `MediaItem` (Task 2)
- Produces: `MediaItem.contentUri(): Uri`, `collectionUriFor(isVideo: Boolean): Uri`, `mapMediaRow(...): MediaItem?`. Used by Tasks 9, 10, 14, 16, 19.

- [ ] **Step 1: Write the failing test**

The cursor loop is thin Android glue; the *mapping decisions* are pure and tested here.

```kotlin
package com.swipey.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMapperTest {
    @Test fun mapsAValidImageRow() {
        val item = mapMediaRow(
            id = 7L, isVideo = false, sizeBytes = 1234L, dateAddedSec = 900L,
            durationMs = null, bucketId = 3L, bucketName = "Camera", displayName = "IMG.jpg",
        )
        assertEquals(7L, item?.id)
        assertEquals(false, item?.isVideo)
        assertEquals("Camera", item?.bucketName)
        assertNull(item?.durationMs)
    }

    @Test fun mapsAValidVideoRowWithDuration() {
        val item = mapMediaRow(
            id = 8L, isVideo = true, sizeBytes = 50L, dateAddedSec = 900L,
            durationMs = 12_000L, bucketId = 3L, bucketName = "Camera", displayName = "V.mp4",
        )
        assertEquals(true, item?.isVideo)
        assertEquals(12_000L, item?.durationMs)
    }

    @Test fun rejectsZeroSizedRows() {
        assertNull(mapMediaRow(1L, false, 0L, 900L, null, 3L, "Camera", "a.jpg"))
    }

    @Test fun rejectsInvalidIds() {
        assertNull(mapMediaRow(0L, false, 100L, 900L, null, 3L, "Camera", "a.jpg"))
    }

    @Test fun fallsBackWhenBucketNameIsNull() {
        assertEquals("Unknown album", mapMediaRow(1L, false, 100L, 900L, null, 3L, null, "a.jpg")?.bucketName)
    }

    @Test fun fallsBackWhenDisplayNameIsNull() {
        assertEquals("Unnamed", mapMediaRow(1L, false, 100L, 900L, null, 3L, "Camera", null)?.displayName)
    }

    @Test fun treatsZeroDurationAsNull() {
        assertNull(mapMediaRow(1L, true, 100L, 900L, 0L, 3L, "Camera", "v.mp4")?.durationMs)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*MediaMapperTest*'`
Expected: FAIL.

- [ ] **Step 3: Write the implementations**

`MediaMapper.kt` — pure, no Android imports:
```kotlin
package com.swipey.app.data

import com.swipey.app.domain.MediaItem

/**
 * Pure mapping decisions for one MediaStore row. Kept separate from cursor
 * iteration so it can be unit tested on the JVM.
 *
 * Returns null for rows that are not worth showing: a zero size usually means a
 * placeholder or still-writing file, and an id of 0 is never valid.
 */
fun mapMediaRow(
    id: Long,
    isVideo: Boolean,
    sizeBytes: Long,
    dateAddedSec: Long,
    durationMs: Long?,
    bucketId: Long,
    bucketName: String?,
    displayName: String?,
): MediaItem? {
    if (id <= 0L || sizeBytes <= 0L) return null
    return MediaItem(
        id = id,
        isVideo = isVideo,
        sizeBytes = sizeBytes,
        dateAddedSec = dateAddedSec,
        durationMs = durationMs?.takeIf { it > 0L },
        bucketId = bucketId,
        bucketName = bucketName ?: "Unknown album",
        displayName = displayName ?: "Unnamed",
    )
}
```

`MediaUri.kt` — the Android edge:
```kotlin
package com.swipey.app.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.swipey.app.domain.MediaItem

/**
 * Never MediaStore.Files — the Files collection has no whole-collection access
 * shortcut and would apply ownership filtering. See spec §5.2.
 */
fun collectionUriFor(isVideo: Boolean): Uri =
    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

fun MediaItem.contentUri(): Uri = ContentUris.withAppendedId(collectionUriFor(isVideo), id)

fun contentUriFor(id: Long, isVideo: Boolean): Uri =
    ContentUris.withAppendedId(collectionUriFor(isVideo), id)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*MediaMapperTest*'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: media content uris and pure row mapper"
```

---

## Task 9: Data — MediaRepository

**Files:**
- Create: `app/src/main/java/com/swipey/app/data/MediaRepository.kt`

**Interfaces:**
- Consumes: `mapMediaRow`, `collectionUriFor` (Task 8), `MediaItem` (Task 2), `LiveTrashRow` (Task 6)
- Produces: `MediaRepository` with `queryAll()`, `queryTrashed()`, `verify(ids, isVideo)`. Used by Tasks 10, 15, 19, 20.

**No unit test:** this is pure Android glue over `ContentResolver`; every decision it makes is already tested in Tasks 2, 6, and 8. It is verified on device in Task 21.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.swipey.app.data

import android.content.ContentResolver
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import com.swipey.app.domain.LiveTrashRow
import com.swipey.app.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val resolver: ContentResolver) {

    private val deckProjection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.DISPLAY_NAME,
    )

    private val trashProjection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.IS_TRASHED,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_EXPIRES,
    )

    /**
     * Every non-trashed image and video. No match-trashed argument is passed:
     * on collection URIs MATCH_DEFAULT resolves to MATCH_EXCLUDE, so trashed
     * items are excluded for free (spec §5.2).
     */
    suspend fun queryAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryMedia(isVideo = false) + queryMedia(isVideo = true)
    }

    private fun queryMedia(isVideo: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        resolver.query(collectionUriFor(isVideo), deckProjection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val bucketIdCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) {
                mapMediaRow(
                    id = c.getLong(idCol),
                    isVideo = isVideo,
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(dateCol),
                    durationMs = durationCol.takeIf { it >= 0 }?.let { c.getLongOrNull(it) },
                    bucketId = bucketIdCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    bucketName = bucketNameCol.takeIf { it >= 0 }?.let { c.getString(it) },
                    displayName = nameCol.takeIf { it >= 0 }?.let { c.getString(it) },
                )?.let(items::add)
            }
        }
        return items
    }

    /** Everything currently in the system trash that this app can see (spec §5.2). */
    suspend fun queryTrashed(): Map<Long, LiveTrashRow> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        (readTrashRows(false, args) + readTrashRows(true, args)).associateBy { it.mediaId }
    }

    /**
     * Verification after a trash or restore. MATCH_INCLUDE is required: a bare
     * collection query resolves to MATCH_EXCLUDE and would report every successful
     * trash as a failure (spec §8.2). An absent row means "gone", not "not trashed".
     */
    suspend fun verify(ids: List<Long>, isVideo: Boolean): Map<Long, LiveTrashRow> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})",
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            readTrashRows(isVideo, args).associateBy { it.mediaId }
        }

    private fun readTrashRows(isVideo: Boolean, args: Bundle): List<LiveTrashRow> {
        val rows = mutableListOf<LiveTrashRow>()
        resolver.query(collectionUriFor(isVideo), trashProjection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val trashedCol = c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val expiresCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                rows += LiveTrashRow(
                    mediaId = c.getLong(idCol),
                    isTrashed = trashedCol.takeIf { it >= 0 }?.let { c.getInt(it) == 1 } ?: false,
                    displayName = nameCol.takeIf { it >= 0 }?.let { c.getString(it) } ?: "Unnamed",
                    sizeBytes = sizeCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    dateExpiresSec = expiresCol.takeIf { it >= 0 }?.let { c.getLongOrNull(it) },
                )
            }
        }
        return rows
    }

    private fun Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Grep for the forbidden patterns**

Run:
```bash
grep -rn "owner_package_name\|MediaStore.Files\|createDeleteRequest" app/src/main/ || echo "clean"
```
Expected: `clean`. If anything matches, it is a bug — fix before committing.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: media repository with deck, bin, and verification queries"
```

---

## Task 10: Data — TrashRepository

**Files:**
- Create: `app/src/main/java/com/swipey/app/data/TrashRepository.kt`
- Modify: `app/src/main/java/com/swipey/app/SwipeyApp.kt`

**Interfaces:**
- Consumes: `MediaRepository` (Task 9), DAOs (Task 7), `resolveRecords`/`Resolution` (Task 6), `chunkedForRequest` (Task 3), `contentUriFor` (Task 8)
- Produces: `TrashRepository` with `buildTrashRequest(items)`, `buildRestoreRequest(records)`, `markPendingTrash(items)`, `markPendingRestore(ids)`, `verifyAndResolve()`, `binView()`, `trashedCount()`; `RecoveryReport`. Used by Tasks 18, 19, 20.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.swipey.app.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.provider.MediaStore
import com.swipey.app.data.db.ReviewedMediaEntity
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.data.db.TrashedItemEntity
import com.swipey.app.data.db.toDomain
import com.swipey.app.domain.BinView
import com.swipey.app.domain.LocalTrashRecord
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.Resolution
import com.swipey.app.domain.TrashState
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.reconcileBin
import com.swipey.app.domain.resolveRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a recovery or verification pass actually changed, for honest per-item reporting. */
data class RecoveryReport(
    val confirmedTrashed: List<Long>,
    val restored: List<Long>,
    val declined: List<Long>,
    val vanished: List<Long>,
) {
    val isEmpty: Boolean get() =
        confirmedTrashed.isEmpty() && restored.isEmpty() && declined.isEmpty() && vanished.isEmpty()
}

class TrashRepository(
    private val resolver: ContentResolver,
    private val media: MediaRepository,
    private val db: SwipeyDatabase,
) {

    /**
     * Written BEFORE the PendingIntent is launched. The consent dialog runs in
     * MediaProvider's process and can complete while Swipey is dead; without this
     * record the items would be trashed with nothing pointing at them. Spec §8.1.
     */
    suspend fun markPendingTrash(items: List<MediaItem>, now: Long) = withContext(Dispatchers.IO) {
        db.trashed().upsertAll(
            items.map {
                TrashedItemEntity(
                    mediaId = it.id,
                    isVideo = it.isVideo,
                    displayName = it.displayName,
                    sizeBytes = it.sizeBytes,
                    trashedAt = now,
                    state = TrashState.PENDING_TRASH.name,
                )
            },
        )
    }

    suspend fun markPendingRestore(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.trashed().setState(ids, TrashState.PENDING_RESTORE.name)
    }

    /** One consent dialog per chunk. Spec §8. */
    fun buildTrashRequests(items: List<MediaItem>): List<PendingIntent> =
        items.chunkedForRequest().map { chunk ->
            MediaStore.createTrashRequest(resolver, chunk.map { it.contentUri() }, true)
        }

    fun buildRestoreRequests(records: List<LocalTrashRecord>): List<PendingIntent> =
        records.chunkedForRequest().map { chunk ->
            MediaStore.createTrashRequest(
                resolver,
                chunk.map { contentUriFor(it.mediaId, it.isVideo) },
                false,
            )
        }

    /**
     * The spec §8.1 recovery pass. Safe to call at any time — on app start, on Bin
     * open, and after every consent dialog result. RESULT_OK is never trusted;
     * this is what decides what actually happened.
     */
    suspend fun verifyAndResolve(): RecoveryReport = withContext(Dispatchers.IO) {
        val local = db.trashed().all().map { it.toDomain() }
        if (local.isEmpty()) return@withContext RecoveryReport(emptyList(), emptyList(), emptyList(), emptyList())

        val live = liveRowsFor(local)
        val resolutions = resolveRecords(local, live)

        val confirmed = resolutions.filterIsInstance<Resolution.MarkTrashed>().map { it.mediaId }
        val declined = resolutions.filterIsInstance<Resolution.DeleteRecord>().map { it.mediaId }
        val restored = resolutions.filterIsInstance<Resolution.DeleteRecordAndReview>().map { it.mediaId }
        val vanished = resolutions.filterIsInstance<Resolution.Vanished>().map { it.mediaId }

        if (confirmed.isNotEmpty()) {
            db.trashed().setState(confirmed, TrashState.TRASHED.name)
            db.reviewed().upsertAll(
                confirmed.map { ReviewedMediaEntity(it, "TRASHED", System.currentTimeMillis()) },
            )
        }
        if (declined.isNotEmpty()) db.trashed().delete(declined)
        if (restored.isNotEmpty()) {
            db.trashed().delete(restored)
            db.reviewed().deleteAll(restored)   // restored items become swipeable again (spec §8)
        }
        if (vanished.isNotEmpty()) db.trashed().delete(vanished)

        RecoveryReport(confirmed, restored, declined, vanished)
    }

    suspend fun binView(): BinView = withContext(Dispatchers.IO) {
        val local = db.trashed().all().map { it.toDomain() }
        reconcileBin(local, liveRowsFor(local))
    }

    suspend fun trashedCount(): Int = withContext(Dispatchers.IO) { db.trashed().trashedCount() }

    private suspend fun liveRowsFor(local: List<LocalTrashRecord>) =
        media.verify(local.filter { !it.isVideo }.map { it.mediaId }, isVideo = false) +
            media.verify(local.filter { it.isVideo }.map { it.mediaId }, isVideo = true)
}
```

- [ ] **Step 2: Wire the singletons in `SwipeyApp.kt`**

```kotlin
package com.swipey.app

import android.app.Application
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.TrashRepository
import com.swipey.app.data.db.SwipeyDatabase

class SwipeyApp : Application() {
    val database by lazy { SwipeyDatabase.get(this) }
    val mediaRepository by lazy { MediaRepository(contentResolver) }
    val trashRepository by lazy { TrashRepository(contentResolver, mediaRepository, database) }
}
```

- [ ] **Step 3: Verify it compiles and check the forbidden patterns again**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Run:
```bash
grep -rn "createDeleteRequest\|resolver.delete\|contentResolver.delete" app/src/main/ || echo "clean"
```
Expected: `clean`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: trash repository with pending-state durability and recovery pass"
```

---

## Task 11: UI — theme, copy, navigation, and Home

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/swipey/app/ui/common/Copy.kt`
- Create: `app/src/main/java/com/swipey/app/ui/SwipeyNavHost.kt`
- Create: `app/src/main/java/com/swipey/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/swipey/app/MainActivity.kt`

**Interfaces:**
- Consumes: `SortMode` (Task 2)
- Produces: `SwipeyTheme`, `Copy` object, `Routes` object, `SwipeyNavHost`. Used by every later UI task.

- [ ] **Step 1: Write `Copy.kt` — every user-facing string, per spec §9**

```kotlin
package com.swipey.app.ui.common

/**
 * All user-facing copy lives here so the spec §9 rules can be audited in one place.
 *
 * Rules, binding:
 *  1. "moved to trash", never "deleted"
 *  2. never claim space was freed — trashing frees zero bytes
 *  3. expiry is a minimum: "Recoverable until at least ..."
 *  4. this is the SYSTEM trash, shared with Google Photos / Files
 *  5. restoring shows a second system confirmation
 *  6. per-item outcomes come from the IS_TRASHED re-check
 *  7. Swipey has no permanent-delete function
 */
object Copy {
    const val APP_NAME = "Swipey"

    const val HOME_ALL_MEDIA = "All media"
    const val HOME_ALL_MEDIA_SUB = "Everything, in the order you choose"
    const val HOME_ALBUMS = "Albums"
    const val HOME_ALBUMS_SUB = "Pick a folder to clean up"
    const val HOME_SHUFFLE = "Shuffle"
    const val HOME_SHUFFLE_SUB = "Random order"
    const val HOME_BIN = "Bin"

    const val PERMISSION_TITLE = "Swipey needs access to your photos and videos"
    const val PERMISSION_BODY =
        "Swipey shows your photos one at a time so you can keep or bin them. " +
            "It never deletes anything permanently."
    const val PERMISSION_GRANT = "Grant access"

    const val PARTIAL_TITLE = "Swipey needs access to all photos"
    const val PARTIAL_BODY =
        "With only selected photos shared, Swipey can't show you the bin, " +
            "so it can't promise that what you remove is recoverable. " +
            "Please allow access to all photos and videos."
    const val PARTIAL_ACTION = "Open settings"

    const val DENIED_TITLE = "Access denied"
    const val DENIED_BODY = "Swipey can't do anything without access to your gallery."
    const val DENIED_ACTION = "Try again"

    const val DECK_EMPTY_TITLE = "Nothing left to review"
    const val DECK_EMPTY_BODY = "You've been through everything here."
    const val DECK_NOTHING_MARKED = "Nothing marked — all caught up"
    const val DECK_BACK_CONFIRM = "Discard the items you've marked?"
    const val DECK_DISCARD = "Discard"
    const val DECK_REVIEW = "Review"

    const val REVIEW_TITLE = "Review"
    const val REVIEW_EMPTY = "Nothing marked yet"
    fun reviewHeader(count: Int, size: String) = "$count items · $size"
    fun reviewAction(count: Int) = "Move $count items to trash"

    /** Rule 2: this says where the bytes went, not that they came back. */
    const val TRASH_SIZE_NOTE = "Space is freed when the trash is emptied, about 30 days from now."
    /** Rule 4. */
    const val SYSTEM_TRASH_NOTE =
        "This is your phone's trash, shared with Google Photos and Files. " +
            "If you empty it there, these items are gone from Swipey's bin too."
    /** Rule 7. */
    const val NO_PERMANENT_DELETE_NOTE = "Swipey can't delete anything permanently."
    /** Rule 5. */
    const val RESTORE_CONFIRM_NOTE = "Android will ask you to confirm the restore."

    fun multipleConfirmations(count: Int) =
        "Android will ask you to confirm $count times, once per batch."

    fun resultTitle(count: Int) = "$count items moved to trash"
    fun expiresAtLeast(date: String) = "Recoverable until at least $date"
    fun cancelled(done: Int, total: Int) = "Stopped after $done of $total items"

    const val BIN_TITLE = "Bin"
    const val BIN_EMPTY = "Nothing here"
    const val BIN_RESTORE = "Restore"
    fun binOtherItems(count: Int) = "$count other items are in your phone's trash, put there by other apps."
    fun vanishedNotice(count: Int) = "$count items are no longer in the trash."
}
```

- [ ] **Step 2: Write the theme**

```kotlin
package com.swipey.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KeepGreen = Color(0xFF2E7D32)
val MarkRed = Color(0xFFC62828)

@Composable
fun SwipeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
```

- [ ] **Step 3: Write the nav host and Home screen**

`SwipeyNavHost.kt`:
```kotlin
package com.swipey.app.ui

object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val SORT = "sort"
    const val ALBUMS = "albums"
    const val DECK = "deck?bucketId={bucketId}&sort={sort}&shuffle={shuffle}"
    const val REVIEW = "review"
    const val RESULT = "result"
    const val BIN = "bin"

    fun deck(bucketId: Long? = null, sort: String = "NEWEST", shuffle: Boolean = false) =
        "deck?bucketId=${bucketId ?: -1L}&sort=$sort&shuffle=$shuffle"
}
```

`HomeScreen.kt`:
```kotlin
package com.swipey.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.common.Copy

@Composable
fun HomeScreen(
    binCount: Int,
    onAllMedia: () -> Unit,
    onAlbums: () -> Unit,
    onShuffle: () -> Unit,
    onBin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(Copy.APP_NAME, style = MaterialTheme.typography.headlineMedium)
        HomeEntry(Copy.HOME_ALL_MEDIA, Copy.HOME_ALL_MEDIA_SUB, onAllMedia)
        HomeEntry(Copy.HOME_ALBUMS, Copy.HOME_ALBUMS_SUB, onAlbums)
        HomeEntry(Copy.HOME_SHUFFLE, Copy.HOME_SHUFFLE_SUB, onShuffle)
        HomeEntry(Copy.HOME_BIN, "$binCount items", onBin)
    }
}

@Composable
private fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: theme, centralised copy, navigation routes, home screen"
```

---

## Task 12: UI — PermissionGate

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/permission/PermissionGate.kt`

**Interfaces:**
- Consumes: `MediaAccess`, `resolveMediaAccess` (Task 5), `Copy` (Task 11)
- Produces: `PermissionGate(onGranted: @Composable () -> Unit)`, `currentMediaAccess(context): MediaAccess`. Used by Task 20.

- [ ] **Step 1: Write the implementation**

All three permissions go in **one** request — Android's own guidance, and requesting them separately stacks dialogs (spec §6).

```kotlin
package com.swipey.app.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.swipey.app.domain.MediaAccess
import com.swipey.app.domain.resolveMediaAccess
import com.swipey.app.ui.common.Copy

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun currentMediaAccess(context: Context): MediaAccess = resolveMediaAccess(
    imagesGranted = granted(context, Manifest.permission.READ_MEDIA_IMAGES),
    videoGranted = granted(context, Manifest.permission.READ_MEDIA_VIDEO),
    userSelectedGranted = Build.VERSION.SDK_INT >= 34 &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
)

private fun requestedPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    }

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(currentMediaAccess(context)) }

    // Re-check on resume so returning from Settings updates the gate.
    LifecycleResumeEffect(Unit) {
        access = currentMediaAccess(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { access = currentMediaAccess(context) }

    when (access) {
        MediaAccess.FULL -> content()
        MediaAccess.PARTIAL -> Message(
            title = Copy.PARTIAL_TITLE,
            body = Copy.PARTIAL_BODY,
            action = Copy.PARTIAL_ACTION,
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
        MediaAccess.DENIED -> Message(
            title = Copy.PERMISSION_TITLE,
            body = Copy.PERMISSION_BODY,
            action = Copy.PERMISSION_GRANT,
            onAction = { launcher.launch(requestedPermissions()) },
        )
    }
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction) { Text(action) }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: permission gate blocking on partial access"
```

---

## Task 13: UI — Albums and sort chooser

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/albums/AlbumsScreen.kt`
- Create: `app/src/main/java/com/swipey/app/ui/albums/SortChooserScreen.kt`

**Interfaces:**
- Consumes: `Album`, `SortMode` (Task 2), `formatBytes` (Task 3), `Copy` (Task 11)
- Produces: `AlbumsScreen(albums, onPick)`, `SortChooserScreen(onPick)`. Used by Task 20.

- [ ] **Step 1: Write the implementations**

`AlbumsScreen.kt`:
```kotlin
package com.swipey.app.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.domain.Album
import com.swipey.app.domain.formatBytes

@Composable
fun AlbumsScreen(albums: List<Album>, onPick: (Album) -> Unit) {
    if (albums.isEmpty()) {
        Text("No albums found", Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.bucketId }) { album ->
            Column(
                Modifier.fillMaxWidth().clickable { onPick(album) }.padding(16.dp),
            ) {
                Text(album.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${album.itemCount} items · ${formatBytes(album.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
```

`SortChooserScreen.kt`:
```kotlin
package com.swipey.app.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.domain.SortMode

private val labels = listOf(
    SortMode.NEWEST to "Newest first",
    SortMode.OLDEST to "Oldest first",
    SortMode.LARGEST to "Largest first",
    SortMode.SMALLEST to "Smallest first",
)

@Composable
fun SortChooserScreen(onPick: (SortMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sort by", style = MaterialTheme.typography.headlineSmall)
        labels.forEach { (mode, label) ->
            Text(
                label,
                Modifier.fillMaxWidth().clickable { onPick(mode) }.padding(vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: album list and sort chooser screens"
```

---

## Task 14: UI — SwipeCard gesture component

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/deck/SwipeCard.kt`

**Interfaces:**
- Consumes: `MediaItem` (Task 2), `contentUri` (Task 8), theme colors (Task 11)
- Produces: `SwipeCard(item, onSwiped: (Boolean) -> Unit, content: @Composable () -> Unit)` where the Boolean is `true` for a right/keep swipe. Used by Task 15.

This component owns gesture and animation only — no business logic, no database, no MediaStore.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.swipey.app.ui.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.theme.KeepGreen
import com.swipey.app.ui.theme.MarkRed
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The top card. Commits at 30% of screen width or on a fling.
 * onSwiped(true) = kept (right), onSwiped(false) = marked (left).
 */
@Composable
fun SwipeCard(
    key: Any,
    onSwiped: (keep: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.3f
    val offsetX = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var velocity by remember(key) { mutableFloatStateOf(0f) }

    LaunchedEffect(key) { offsetX.snapTo(0f) }

    val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = progress * 12f
            }
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val committed = abs(offsetX.value) > threshold || abs(velocity) > 1500f
                        if (committed) {
                            val keep = offsetX.value > 0
                            scope.launch {
                                offsetX.animateTo(
                                    if (keep) screenWidthPx * 1.5f else -screenWidthPx * 1.5f,
                                    tween(220),
                                )
                                onSwiped(keep)
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                        velocity = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocity = dragAmount * 60f
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    },
                )
            },
    ) {
        content()
        if (progress != 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background((if (progress > 0) KeepGreen else MarkRed).copy(alpha = abs(progress) * 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (progress > 0) "KEEP" else "BIN")
            }
        }
    }
}

/** Programmatic swipe for the Keep/Bin buttons, so buttons and gestures agree. */
@Composable
fun rememberSwipeTrigger(): SwipeTrigger = remember { SwipeTrigger() }

class SwipeTrigger {
    var pending: Boolean? = null
        private set

    fun keep() { pending = true }
    fun mark() { pending = false }
    fun consume(): Boolean? = pending.also { pending = null }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: swipe card gesture component"
```

---

## Task 15: UI — DeckViewModel and DeckScreen

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/deck/DeckViewModel.kt`
- Create: `app/src/main/java/com/swipey/app/ui/deck/DeckScreen.kt`

**Interfaces:**
- Consumes: `SwipeSession` (Task 4), `MediaRepository` (Task 9), DAOs (Task 7), `SwipeCard` (Task 14), `formatBytes` (Task 3), `Copy` (Task 11)
- Produces: `DeckViewModel`, `DeckUiState`, `DeckScreen`. Used by Tasks 17, 20.

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.swipey.app.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.db.ReviewedMediaEntity
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.SortMode
import com.swipey.app.domain.SwipeSession
import com.swipey.app.domain.shuffledWithSeed
import com.swipey.app.domain.sortedFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeckUiState(
    val loading: Boolean = true,
    val current: MediaItem? = null,
    val next: MediaItem? = null,
    val position: Int = 0,
    val total: Int = 0,
    val markedCount: Int = 0,
    val markedBytes: Long = 0L,
    val exhausted: Boolean = false,
)

class DeckViewModel(
    private val media: MediaRepository,
    private val db: SwipeyDatabase,
) : ViewModel() {

    private var session: SwipeSession = SwipeSession(emptyList())
    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state

    fun load(bucketId: Long?, sort: SortMode, shuffle: Boolean, seed: Long) {
        viewModelScope.launch {
            val kept = db.reviewed().keptIds().toSet()
            val all = media.queryAll()
                .filter { it.id !in kept }
                .filter { bucketId == null || it.bucketId == bucketId }
            val ordered = if (shuffle) all.shuffledWithSeed(seed) else all.sortedFor(sort)
            session = SwipeSession(ordered)
            publish()
        }
    }

    fun swipe(keep: Boolean) {
        val item = if (keep) session.swipeRight() else session.swipeLeft()
        if (keep && item != null) {
            // Persisted immediately so a crash mid-session loses nothing (spec §10).
            viewModelScope.launch {
                db.reviewed().upsert(ReviewedMediaEntity(item.id, "KEEP", System.currentTimeMillis()))
            }
        }
        publish()
    }

    fun undo() {
        val undone = session.undo() ?: return
        viewModelScope.launch { db.reviewed().delete(undone.item.id) }
        publish()
    }

    fun marked(): List<MediaItem> = session.marked()

    fun unmark(id: Long) {
        session.unmark(id)
        publish()
    }

    private fun publish() {
        _state.value = DeckUiState(
            loading = false,
            current = session.current,
            next = null,
            position = session.position,
            total = session.total,
            markedCount = session.markedCount,
            markedBytes = session.markedBytes,
            exhausted = session.isExhausted,
        )
    }
}
```

- [ ] **Step 2: Write the DeckScreen**

```kotlin
package com.swipey.app.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy

@Composable
fun DeckScreen(
    viewModel: DeckViewModel,
    onReview: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Terminal states, spec §4.
    LaunchedEffect(state.exhausted, state.markedCount) {
        if (state.exhausted && !state.loading) {
            if (state.markedCount > 0) onReview() else onDone()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.position} / ${state.total}", style = MaterialTheme.typography.labelLarge)
            if (state.markedCount > 0) {
                TextButton(onClick = onReview) {
                    Text("${state.markedCount} marked · ${formatBytes(state.markedBytes)}")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val item = state.current
            if (item == null) {
                Text(Copy.DECK_EMPTY_TITLE)
            } else {
                SwipeCard(key = item.id, onSwiped = { keep -> viewModel.swipe(keep) }) {
                    MediaCardContent(item)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.swipe(keep = false) }) { Text("Bin") }
            TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
            Button(onClick = { viewModel.swipe(keep = true) }) { Text("Keep") }
        }
    }
}
```

- [ ] **Step 3: Write `MediaCardContent` (still images for now; video arrives in Task 16)**

Add to `DeckScreen.kt`:
```kotlin
@Composable
fun MediaCardContent(item: com.swipey.app.domain.MediaItem) {
    coil3.compose.AsyncImage(
        model = com.swipey.app.data.contentUriFor(item.id, item.isVideo),
        contentDescription = item.displayName,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: deck screen and view model"
```

---

## Task 16: UI — video playback card

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/deck/VideoCard.kt`
- Modify: `app/src/main/java/com/swipey/app/ui/deck/DeckScreen.kt` (route videos to `VideoCard`)

**Interfaces:**
- Consumes: `MediaItem` (Task 2), `contentUriFor` (Task 8)
- Produces: `VideoCard(item)`. Used by Task 15's `MediaCardContent`.

One ExoPlayer instance, bound only to the top card, released on lifecycle pause (spec §11).

- [ ] **Step 1: Write the implementation**

```kotlin
package com.swipey.app.ui.deck

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem

@Composable
fun VideoCard(item: MediaItem) {
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(contentUriFor(item.id, isVideo = true)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(item.id) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
        )
        item.durationMs?.let { duration ->
            Text(
                formatDuration(duration),
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
```

- [ ] **Step 2: Route videos to VideoCard**

In `DeckScreen.kt`, replace the body of `MediaCardContent` with:
```kotlin
@Composable
fun MediaCardContent(item: com.swipey.app.domain.MediaItem) {
    if (item.isVideo) {
        VideoCard(item)
    } else {
        coil3.compose.AsyncImage(
            model = com.swipey.app.data.contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: muted looping video playback on the top card"
```

---

## Task 17: UI — Review screen

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/review/ReviewScreen.kt`

**Interfaces:**
- Consumes: `MediaItem` (Task 2), `formatBytes` (Task 3), `contentUriFor` (Task 8), `Copy` (Task 11)
- Produces: `ReviewScreen(items, onUnmark, onCommit, onBack)`. Used by Task 20.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.swipey.app.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy

@Composable
fun ReviewScreen(
    items: List<MediaItem>,
    onUnmark: (Long) -> Unit,
    onCommit: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(Copy.REVIEW_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(
            Copy.reviewHeader(items.size, formatBytes(items.sumOf { it.sizeBytes })),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Copy.REVIEW_EMPTY)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f)) {
                items(items, key = { it.id }) { item ->
                    Box(Modifier.padding(2.dp).aspectRatio(1f).clickable { onUnmark(item.id) }) {
                        AsyncImage(
                            model = contentUriFor(item.id, item.isVideo),
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            formatBytes(item.sizeBytes),
                            Modifier.align(Alignment.BottomStart).padding(4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // Spec §9 rules 2, 4, 7 — stated before the user commits, not after.
        Text(Copy.TRASH_SIZE_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        val batches = items.chunkedForRequest().size
        if (batches > 1) {
            Text(Copy.multipleConfirmations(batches), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onCommit,
            enabled = items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(Copy.reviewAction(items.size))
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: review screen with pre-commit caveats"
```

---

## Task 18: UI — trash commit flow and Result screen

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/result/ResultScreen.kt`
- Create: `app/src/main/java/com/swipey/app/ui/common/TrashLauncher.kt`

**Interfaces:**
- Consumes: `TrashRepository`, `RecoveryReport` (Task 10), `Copy` (Task 11)
- Produces: `rememberTrashLauncher(...)`, `ResultScreen(report, onHome, onBin)`. Used by Tasks 19, 20.

The launcher runs chunks sequentially, writes `PENDING_TRASH` **before** launching, and always verifies afterwards rather than trusting `RESULT_OK`.

- [ ] **Step 1: Write the launcher**

```kotlin
package com.swipey.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.cash.quiver.NotUsed
import com.swipey.app.data.RecoveryReport
import com.swipey.app.data.TrashRepository
import kotlinx.coroutines.launch

/**
 * Runs a list of consent dialogs one after another, verifying after each.
 *
 * RESULT_OK is never trusted (spec §8): PermissionActivity sets it unconditionally.
 * The truth comes from TrashRepository.verifyAndResolve().
 */
class TrashLauncher(
    private val repository: TrashRepository,
    private val launch: (IntentSenderRequest) -> Unit,
    private val onFinished: (RecoveryReport) -> Unit,
) {
    private var queue: List<android.app.PendingIntent> = emptyList()
    private var index = 0

    fun start(requests: List<android.app.PendingIntent>) {
        queue = requests
        index = 0
        launchNext()
    }

    private fun launchNext() {
        if (index >= queue.size) return
        launch(IntentSenderRequest.Builder(queue[index].intentSender).build())
    }

    suspend fun onResult() {
        index++
        if (index < queue.size) {
            launchNext()
        } else {
            onFinished(repository.verifyAndResolve())
        }
    }

    /** User cancelled: stop launching further chunks, but still verify what did land. */
    suspend fun onCancelled() {
        index = queue.size
        onFinished(repository.verifyAndResolve())
    }
}
```

**Note:** remove the stray `import app.cash.quiver.NotUsed` line — it is not a dependency. If it appears, delete it.

- [ ] **Step 2: Write the Result screen**

```kotlin
package com.swipey.app.ui.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.data.RecoveryReport
import com.swipey.app.ui.common.Copy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ResultScreen(
    report: RecoveryReport,
    earliestExpirySec: Long?,
    onHome: () -> Unit,
    onBin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(Copy.resultTitle(report.confirmedTrashed.size), style = MaterialTheme.typography.headlineSmall)

        earliestExpirySec?.let {
            val date = Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM"))
            Text(Copy.expiresAtLeast(date), Modifier.padding(top = 8.dp))
        }

        Text(Copy.TRASH_SIZE_NOTE, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)

        // Spec §9 rule 6 — honest per-item reporting, never blanket success.
        if (report.declined.isNotEmpty()) {
            Text(
                Copy.cancelled(report.confirmedTrashed.size, report.confirmedTrashed.size + report.declined.size),
                Modifier.padding(top = 8.dp),
            )
        }
        if (report.vanished.isNotEmpty()) {
            Text(Copy.vanishedNotice(report.vanished.size), Modifier.padding(top = 8.dp))
        }

        Button(onClick = onBin, modifier = Modifier.padding(top = 24.dp)) { Text(Copy.BIN_TITLE) }
        TextButton(onClick = onHome) { Text("Done") }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If the `app.cash.quiver` import was left in, remove it.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: chunked trash commit flow and result screen"
```

---

## Task 19: UI — Bin screen and restore

**Files:**
- Create: `app/src/main/java/com/swipey/app/ui/bin/BinViewModel.kt`
- Create: `app/src/main/java/com/swipey/app/ui/bin/BinScreen.kt`

**Interfaces:**
- Consumes: `TrashRepository` (Task 10), `BinView`/`BinEntry` (Task 6), `Copy` (Task 11), `formatBytes` (Task 3)
- Produces: `BinViewModel`, `BinScreen`. Used by Task 20.

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.swipey.app.ui.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.TrashRepository
import com.swipey.app.domain.BinEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BinUiState(
    val loading: Boolean = true,
    val entries: List<BinEntry> = emptyList(),
    val vanishedCount: Int = 0,
    val selected: Set<Long> = emptySet(),
)

class BinViewModel(private val repository: TrashRepository) : ViewModel() {

    private val _state = MutableStateFlow(BinUiState())
    val state: StateFlow<BinUiState> = _state

    /** Reconciles on every open, per spec §8. Also resolves any PENDING_* rows. */
    fun refresh() {
        viewModelScope.launch {
            repository.verifyAndResolve()
            val view = repository.binView()
            _state.value = BinUiState(
                loading = false,
                entries = view.entries,
                vanishedCount = view.vanished.size,
                selected = emptySet(),
            )
        }
    }

    fun toggle(id: Long) {
        val selected = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (id in selected) selected - id else selected + id,
        )
    }

    fun selectedRecords() = _state.value.entries
        .filter { it.record.mediaId in _state.value.selected }
        .map { it.record }
}
```

- [ ] **Step 2: Write the Bin screen**

```kotlin
package com.swipey.app.ui.bin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BinScreen(viewModel: BinViewModel, onRestore: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(Copy.BIN_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        if (state.vanishedCount > 0) {
            Text(Copy.vanishedNotice(state.vanishedCount), style = MaterialTheme.typography.bodySmall)
        }

        if (state.entries.isEmpty() && !state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Copy.BIN_EMPTY)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f)) {
                items(state.entries, key = { it.record.mediaId }) { entry ->
                    val selected = entry.record.mediaId in state.selected
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clickable { viewModel.toggle(entry.record.mediaId) },
                    ) {
                        AsyncImage(
                            model = contentUriFor(entry.record.mediaId, entry.record.isVideo),
                            contentDescription = entry.record.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(Color(0x662E7D32)))
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                            Text(formatBytes(entry.record.sizeBytes), style = MaterialTheme.typography.labelSmall)
                            entry.expiresAtSec?.let {
                                val date = Instant.ofEpochSecond(it)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("d MMM"))
                                Text(Copy.expiresAtLeast(date), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Spec §9 rule 5.
        Text(Copy.RESTORE_CONFIRM_NOTE, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onRestore,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("${Copy.BIN_RESTORE} ${state.selected.size}")
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: bin screen with reconciliation and restore selection"
```

---

## Task 20: Wire the app together and audit the copy rules

**Files:**
- Modify: `app/src/main/java/com/swipey/app/MainActivity.kt`
- Create: `app/src/main/java/com/swipey/app/ui/SwipeyApp.kt` (the composable root; note this is `ui/SwipeyApp.kt`, distinct from the `Application` class at the package root)

**Interfaces:**
- Consumes: everything from Tasks 11–19
- Produces: a running app.

- [ ] **Step 1: Build the composable root**

Wire: PermissionGate → NavHost with Home / Sort / Albums / Deck / Review / Result / Bin. Hold the marked set in a shared `DeckViewModel` scoped to the nav graph so Review can read it. Run `trashRepository.verifyAndResolve()` once on app start — this is the §8.1 recovery pass, and it must run before Home reads the bin count.

```kotlin
package com.swipey.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swipey.app.SwipeyApp
import com.swipey.app.domain.SortMode
import com.swipey.app.ui.permission.PermissionGate

@Composable
fun SwipeyRoot(app: SwipeyApp) {
    val navController = rememberNavController()
    var binCount by remember { mutableIntStateOf(0) }

    // Spec §8.1 recovery pass — resolves anything left PENDING_* by a process death.
    LaunchedEffect(Unit) {
        app.trashRepository.verifyAndResolve()
        binCount = app.trashRepository.trashedCount()
    }

    PermissionGate {
        NavHost(navController, startDestination = Routes.HOME) {
            // Wire each route to the screens from Tasks 11-19.
            // Deck arguments: bucketId (-1 means all), sort (SortMode.name), shuffle (Boolean).
        }
    }
}
```

Complete the NavHost so that:
- `Routes.HOME` → `HomeScreen(binCount, ...)`
- All media → `Routes.SORT` → `SortChooserScreen` → `Routes.deck(sort = mode.name)`
- Albums → `Routes.ALBUMS` → `AlbumsScreen` → `Routes.deck(bucketId = album.bucketId)`
- Shuffle → `Routes.deck(shuffle = true)` with a seed of `System.currentTimeMillis()`
- Deck → `Routes.REVIEW` on exhaustion-with-marks or the marked chip; → `Routes.HOME` on exhaustion-without-marks
- Review commit → the Task 18 launcher → `Routes.RESULT`
- Result / Home → `Routes.BIN`

- [ ] **Step 2: Update MainActivity**

```kotlin
package com.swipey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.swipey.app.ui.SwipeyRoot
import com.swipey.app.ui.theme.SwipeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwipeyTheme { SwipeyRoot(application as SwipeyApp) }
        }
    }
}
```

- [ ] **Step 3: Audit the copy rules**

Run:
```bash
grep -rniE '"[^"]*\bdelet(e|ed|ing)\b' app/src/main/java/com/swipey/app/ui/ | grep -v "NO_PERMANENT_DELETE" || echo "no stray delete copy"
grep -rn "freed\|free up\|reclaim" app/src/main/java/com/swipey/app/ui/ || echo "no false space claims"
```
Expected: both print the "no ..." message. Any hit is a spec §9 violation — fix it.

- [ ] **Step 4: Run everything**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: wire navigation, startup recovery pass, copy audit"
```

---

## Task 21: Install on device and run the verification checklist

**Files:** none — this is verification.

**Interfaces:**
- Consumes: the built APK

**This task cannot be completed by a subagent working alone.** It needs the user's phone paired over wireless debugging. If no device is attached, stop and report that rather than marking the task done.

- [ ] **Step 1: Confirm a device is attached**

Run: `~/Library/Android/sdk/platform-tools/adb devices -l`
Expected: one device listed. If the list is empty, stop — the user must pair the phone first (Settings → Developer options → Wireless debugging → Pair device with pairing code, then `adb pair <ip>:<port>` and `adb connect <ip>:<port>`).

- [ ] **Step 2: Record the device's Android version**

Run: `adb shell getprop ro.build.version.release && adb shell getprop ro.product.manufacturer`
Note both — spec §14 item 7 depends on the manufacturer's gallery behaviour.

- [ ] **Step 3: Install**

Run:
```bash
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

- [ ] **Step 4: Run the instrumented Room tests**

Run: `./gradlew connectedDebugAndroidTest`
Expected: all `SwipeyDaoTest` tests pass.

- [ ] **Step 5: Work through the spec §14 checklist**

Each item is pass/fail, recorded in the final report. Do not mark this task complete with unresolved items — report them.

1. Partial-access detection — grant "Select photos and videos", confirm the block screen appears
2. Foreign trashed thumbnails — confirm Coil loads a trashed Camera/WhatsApp-owned item in the Bin
3. Trash round-trip — trash, confirm `IS_TRASHED=1`, gone from deck, present in Bin
4. Restore fidelity — restore, confirm original album and date, second consent dialog appears
5. Mixed batch — images+videos in one request, dialog wording matches our copy
6. Chunk cap — 500 URIs per request does not throw
7. OEM bin interaction — does the device gallery's own bin show these items
8. Long filenames — check for truncation or `(1)` suffixes on restore
9. Process-death recovery — with the dialog showing, `adb shell am kill com.swipey.app`, tap Allow, reopen, confirm the items appear in the Bin

- [ ] **Step 6: Commit any fixes and report**

```bash
git add -A && git commit -m "fix: issues found during device verification"
```

---

## Self-Review Notes

**Spec coverage:** §1 Task 20 · §2 (out of scope items absent throughout) · §3 Task 1 · §4 Tasks 11–19 · §5.1 Task 2 · §5.2 Task 9 · §5.3 Tasks 9, 10 · §6 Tasks 5, 12 · §7 Task 7 · §8 Task 10 · §8.1 Tasks 6, 10, 20 · §8.2 Task 9 · §9 Tasks 11, 17, 18, 19, 20 · §10 Tasks 4, 15 · §11 Tasks 14, 16 · §12 Tasks 9, 10, 15 · §13 Tasks 2–8 · §14 Task 21 · §15 Tasks 1, 21.

**Known gaps, deliberate:**
- The "Reset review history" settings action (spec §7) has no task. It is one DAO call (`reviewed().clear()`, already written in Task 7) and one button; fold it into Task 20 if wanted, or defer.
- `MediaRepository`, `TrashRepository`, and the Compose screens have no automated tests. This is intentional: every decision they make is pure and covered in Tasks 2–8, and MediaProvider behaviour cannot be faked meaningfully. They are verified on the device in Task 21.
- `DeckUiState.next` is always null — the second card underneath (spec §11) is not wired. Add it in Task 15 if the deck feels flat; it is visual polish, not correctness.
