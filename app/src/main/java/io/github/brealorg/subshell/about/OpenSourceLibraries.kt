package io.github.brealorg.subshell.about

import android.content.Context
import androidx.annotation.RawRes
import io.github.brealorg.subshell.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ISSUE39_LICENCES_AND_SOURCE_IN_THE_APP_V1 (#39): what Help & about shows:
// subshell's own licence, the source link and every library with its licence.
// Outside ui/ because the library list names data-layer projects (OkHttp,
// kotlinx.serialization) that the ui may not import.

internal const val SUBSHELL_SOURCE_URL = "https://github.com/brealorg/subshell-source-public"

internal const val SUBSHELL_LICENCE_NOTICE =
    "subshell is free software: you can redistribute it and change it under the GNU General " +
        "Public License, version 3 or later. It comes with no warranty."

internal enum class OpenSourceLicence(val title: String, @RawRes val text: Int) {
    GPL_3_0_OR_LATER("GNU General Public License v3.0 or later", R.raw.licence_gpl_3_0),
    APACHE_2_0("Apache License 2.0", R.raw.licence_apache_2_0),
    MIT_CHECKER_QUAL("MIT License", R.raw.licence_mit_checker_qual),
}

internal data class OpenSourceLibrary(
    val name: String,
    val artifact: String,
    val licence: OpenSourceLicence = OpenSourceLicence.APACHE_2_0,
)

/**
 * Every library in the release APK, from `:app:dependencies --configuration
 * releaseRuntimeClasspath` (178 modules on 2026-11-02), one row per AndroidX
 * group or project; licences from each POM. Add a row with a new dependency.
 */
internal val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary("AndroidX Activity", "androidx.activity.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Annotation", "androidx.annotation.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX AppCompat", "androidx.appcompat.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Arch Core", "androidx.arch.core.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Autofill", "androidx.autofill.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Biometric", "androidx.biometric.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Browser", "androidx.browser.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Collection", "androidx.collection.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose BOM", "androidx.compose.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Animation", "androidx.compose.animation.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Foundation", "androidx.compose.foundation.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Material", "androidx.compose.material.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Material 3", "androidx.compose.material3.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Material 3 Adaptive", "androidx.compose.material3.adaptive.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose Runtime", "androidx.compose.runtime.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Compose UI", "androidx.compose.ui.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Concurrent", "androidx.concurrent.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Core", "androidx.core.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX CursorAdapter", "androidx.cursoradapter.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX CustomView", "androidx.customview.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX DataStore", "androidx.datastore.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX DrawerLayout", "androidx.drawerlayout.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Emoji2", "androidx.emoji2.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX ExifInterface", "androidx.exifinterface.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Fragment", "androidx.fragment.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Glance", "androidx.glance.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Graphics", "androidx.graphics.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Interpolator", "androidx.interpolator.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Lifecycle", "androidx.lifecycle.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Loader", "androidx.loader.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Media", "androidx.media.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Media3", "androidx.media3.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Metrics", "androidx.metrics.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Navigation Event", "androidx.navigationevent.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX ProfileInstaller", "androidx.profileinstaller.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX RecyclerView", "androidx.recyclerview.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Room", "androidx.room.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX SavedState", "androidx.savedstate.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX SQLite", "androidx.sqlite.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Startup", "androidx.startup.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Tracing", "androidx.tracing.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX VectorDrawable", "androidx.vectordrawable.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX VersionedParcelable", "androidx.versionedparcelable.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX ViewPager", "androidx.viewpager.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX Window", "androidx.window.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("AndroidX WorkManager", "androidx.work.*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("OkHttp", "com.squareup.okhttp3:okhttp", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Okio", "com.squareup.okio:okio", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Kotlin standard library", "org.jetbrains.kotlin:kotlin-stdlib", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("JetBrains Java Annotations", "org.jetbrains:annotations", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("kotlinx.coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("kotlinx.serialization", "org.jetbrains.kotlinx:kotlinx-serialization-*", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Guava", "com.google.guava:guava", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Guava failureaccess and listenablefuture", "com.google.guava:failureaccess, listenablefuture", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Error Prone annotations", "com.google.errorprone:error_prone_annotations", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("J2ObjC annotations", "com.google.j2objc:j2objc-annotations", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("JSR 305 annotations", "com.google.code.findbugs:jsr305", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("JSpecify", "org.jspecify:jspecify", OpenSourceLicence.APACHE_2_0),
    OpenSourceLibrary("Checker Framework qualifiers", "org.checkerframework:checker-qual", OpenSourceLicence.MIT_CHECKER_QUAL),
)

internal suspend fun readLicenceText(context: Context, licence: OpenSourceLicence): String =
    withContext(Dispatchers.IO) {
        reflowLicenceText(context.resources.openRawResource(licence.text).bufferedReader().use { it.readText() })
    }

/**
 * Licence files are hard-wrapped at ~72 columns, which breaks every line twice
 * on a phone. Lines within a paragraph are joined; blank lines stay paragraph
 * breaks, and indentation is dropped so headings and terms flow like text.
 */
internal fun reflowLicenceText(raw: String): String =
    raw.replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
