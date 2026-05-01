package org.briarproject.briar.android.compose

/**
 * Compile-time probe: if this class compiles, Compose deps are wired
 * and the Kotlin Android plugin is active on briar-android.
 */
@Suppress("unused")
object ComposeProbe {
	val ui: String = androidx.compose.ui.platform.LocalContext.current.applicationInfo.sourceDir
}
