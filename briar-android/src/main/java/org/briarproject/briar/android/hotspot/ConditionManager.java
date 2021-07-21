package org.briarproject.briar.android.hotspot;

import android.net.wifi.WifiManager;

import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;

/**
 * Abstract base class for the ConditionManagers that ensure that the conditions
 * to open a hotspot are fulfilled. There are different extensions of this for
 * API levels lower than 29 and 29+.
 */
abstract class ConditionManager {

	enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	protected final Consumer<Boolean> permissionUpdateCallback;
	protected FragmentActivity ctx;
	WifiManager wifiManager;

	ConditionManager(Consumer<Boolean> permissionUpdateCallback) {
		this.permissionUpdateCallback = permissionUpdateCallback;
	}

	/**
	 * Pass a FragmentActivity context here during `onCreateView()`.
	 */
	void init(FragmentActivity ctx) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
	}

	/**
	 * Call this during onStart() in the fragment where the ConditionManager
	 * is used.
	 */
	abstract void onStart();

	/**
	 * Check if all required conditions are met such that the hotspot can be
	 * started. If any precondition is not met yet, bring up relevant dialogs
	 * asking the user to grant relevant permissions or take relevant actions.
	 *
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	abstract boolean checkAndRequestConditions();

}
