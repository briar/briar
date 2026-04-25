package org.briarproject.briar.android.settings

import android.content.Intent
import android.content.Intent.ACTION_MANAGE_NETWORK_USAGE
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
import org.briarproject.briar.R
import org.briarproject.briar.android.activity.ActivityComponent
import org.briarproject.briar.android.activity.BriarActivity
import org.briarproject.nullsafety.MethodsNotNullByDefault
import org.briarproject.nullsafety.ParametersNotNullByDefault

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class SettingsActivity : BriarActivity(), OnPreferenceStartFragmentCallback {

	companion object {
		const val EXTRA_OPEN_TELEGRAM_SETUP = "openTelegramSetup"
		const val EXTRA_THEME_CHANGE = "themeChange"

		/**
		 * If the preference is not yet enabled, this enables the preference
		 * and makes it persist changed values.
		 * Call this after setting the initial value
		 * to prevent this change from getting persisted in the DB unnecessarily.
		 */
		@JvmStatic
		fun enableAndPersist(pref: Preference) {
			if (!pref.isEnabled) {
				pref.isEnabled = true
				pref.isPersistent = true
			}
		}
	}

	override fun injectActivity(component: ActivityComponent) {
		component.inject(this)
	}

	override fun onCreate(bundle: Bundle?) {
		super.onCreate(bundle)

		supportActionBar?.apply {
			setHomeButtonEnabled(true)
			setDisplayHomeAsUpEnabled(true)
		}

		val launchIntent = intent
		val extras = launchIntent.extras
		if (bundle == null && extras != null &&
				extras.getBoolean(EXTRA_THEME_CHANGE, false)) {
			// show display fragment after theme change
			val fragmentManager = supportFragmentManager
			showNextFragment(fragmentManager, DisplayFragment())
		} else if (bundle == null &&
				ACTION_MANAGE_NETWORK_USAGE == launchIntent.action) {
			// show connection if coming from network settings
			val fragmentManager = supportFragmentManager
			showNextFragment(fragmentManager, ConnectionsFragment())
		}

		setContentView(R.layout.activity_settings)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			onBackPressed()
			return true
		}
		return false
	}

	override fun onPreferenceStartFragment(
			caller: PreferenceFragmentCompat,
			pref: Preference
	): Boolean {
		val fragmentManager = supportFragmentManager
		val fragmentFactory: FragmentFactory = fragmentManager.fragmentFactory
		val fragment = fragmentFactory.instantiate(
				classLoader, requireNotNull(pref.fragment)
		)
		fragment.setTargetFragment(caller, 0)
		// Replace the existing Fragment with the new Fragment
		showNextFragment(fragmentManager, fragment)
		return true
	}

	fun isTelegramConnectorReady(): Boolean =
			getBriarController().isTelegramConnectorReady()

	fun consumeOpenTelegramSetup(): Boolean {
		val openTelegramSetup = intent.getBooleanExtra(
				EXTRA_OPEN_TELEGRAM_SETUP, false
		)
		intent.removeExtra(EXTRA_OPEN_TELEGRAM_SETUP)
		return openTelegramSetup
	}

	private fun showNextFragment(fragmentManager: FragmentManager, f: Fragment) {
		fragmentManager.beginTransaction()
				.setCustomAnimations(
						R.anim.step_next_in,
						R.anim.step_previous_out,
						R.anim.step_previous_in,
						R.anim.step_next_out
				)
				.replace(R.id.fragmentContainer, f)
				.addToBackStack(null)
				.commit()
	}
}
