package org.briarproject.briar.android.navdrawer;

import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;

import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import static android.os.Build.VERSION.SDK_INT;
import static android.transition.TransitionManager.beginDelayedTransition;
import static android.view.View.FOCUS_DOWN;
import static androidx.core.content.ContextCompat.getColor;
import static org.briarproject.bramble.api.plugin.Plugin.REASON_USER;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_BATTERY;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_COUNTRY_BLOCKED;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_MOBILE_DATA;
import static org.briarproject.briar.android.navdrawer.NavDrawerViewModel.TRANSPORT_IDS;
import static org.briarproject.briar.android.util.UiUtils.getDialogIcon;

class PluginViewController {

	private final AppCompatActivity activity;
	private final NavDrawerViewModel viewModel;
	private final ConstraintLayout drawerContent;
	private final ConstraintSet collapsedConstraints, expandedConstraints;
	private final AppCompatImageButton chevronView;
	private final ImageView torIcon, wifiIcon, btIcon;
	private final SwitchCompat torSwitch, wifiSwitch, btSwitch;

	private boolean expanded = false;

	PluginViewController(View v, AppCompatActivity activity,
			NavDrawerViewModel viewModel) {
		this.activity = activity;
		this.viewModel = viewModel;
		drawerContent = v.findViewById(R.id.drawerContent);

		collapsedConstraints = new ConstraintSet();
		collapsedConstraints.clone(v.getContext(),
				R.layout.navigation_menu_collapsed);

		expandedConstraints = new ConstraintSet();
		expandedConstraints.clone(v.getContext(),
				R.layout.navigation_menu_expanded);

		// Scroll the drawer to the bottom when the view is expanded/collapsed
		ScrollView scrollView = v.findViewById(R.id.drawerScrollView);
		drawerContent.addOnLayoutChangeListener((view, left, top, right,
				bottom, oldLeft, oldTop, oldRight, oldBottom) ->
				scrollView.fullScroll(FOCUS_DOWN));

		// Clicking the chevron expands or collapses the view
		chevronView = v.findViewById(R.id.chevronView);
		chevronView.setOnClickListener(view -> expandOrCollapseView());

		// The whole view is clickable when collapsed
		v.findViewById(R.id.connectionsBackground).setOnClickListener(view ->
				expandOrCollapseView());

		torIcon = v.findViewById(R.id.torIcon);
		wifiIcon = v.findViewById(R.id.wifiIcon);
		btIcon = v.findViewById(R.id.btIcon);

		torSwitch = v.findViewById(R.id.torSwitch);
		wifiSwitch = v.findViewById(R.id.wifiSwitch);
		btSwitch = v.findViewById(R.id.btSwitch);

		for (TransportId t : TRANSPORT_IDS) {
			// a OnCheckedChangeListener would get triggered on programmatic updates
			SwitchCompat switchCompat = getSwitch(t);
			switchCompat.setOnClickListener(buttonView -> {
				if (switchCompat.isChecked()) tryToEnablePlugin(t);
				else viewModel.setPluginEnabled(t, false);
				// Revert the switch to its previous state until the plugin
				// changes its state
				switchCompat.toggle();
			});
			viewModel.getPluginState(t).observe(activity, state ->
					stateUpdate(t, state));
		}
	}

	private void expandOrCollapseView() {
		if (SDK_INT >= 19) beginDelayedTransition(drawerContent);
		if (expanded) {
			collapsedConstraints.applyTo(drawerContent);
			chevronView.setImageResource(R.drawable.chevron_up_white);
		} else {
			expandedConstraints.applyTo(drawerContent);
			chevronView.setImageResource(R.drawable.chevron_down_white);
		}
		expanded = !expanded;
	}

	private void tryToEnablePlugin(TransportId id) {
		if (id.equals(TorConstants.ID)) {
			int reasons = viewModel.getReasonsDisabled(id);
			if (reasons == 0 || reasons == REASON_USER) {
				viewModel.setPluginEnabled(id, true);
			} else {
				showTorSettingsDialog(reasons);
			}
		} else {
			viewModel.setPluginEnabled(id, true);
		}
	}

	private void stateUpdate(TransportId id, State state) {
		updateIcon(getIcon(id), state);
		updateSwitch(getSwitch(id), state);
	}

	private SwitchCompat getSwitch(TransportId id) {
		if (id == TorConstants.ID) return torSwitch;
		if (id == BluetoothConstants.ID) return btSwitch;
		if (id == LanTcpConstants.ID) return wifiSwitch;
		throw new AssertionError();
	}

	private void updateSwitch(SwitchCompat switchCompat, State state) {
		boolean checked = state != STARTING_STOPPING && state != DISABLED;
		switchCompat.setChecked(checked);
		switchCompat.setEnabled(state != STARTING_STOPPING);
	}

	private ImageView getIcon(TransportId id) {
		if (id == TorConstants.ID) return torIcon;
		if (id == BluetoothConstants.ID) return btIcon;
		if (id == LanTcpConstants.ID) return wifiIcon;
		throw new AssertionError();
	}

	private void updateIcon(ImageView icon, State state) {
		int colorRes;
		if (state == ACTIVE) {
			colorRes = R.color.briar_green_light;
		} else if (state == ENABLING) {
			colorRes = R.color.briar_yellow;
		} else {
			colorRes = android.R.color.tertiary_text_light;
		}
		int color = getColor(icon.getContext(), colorRes);
		icon.setColorFilter(color);
	}

	private void showTorSettingsDialog(int reasonsDisabled) {
		boolean battery = (reasonsDisabled & REASON_BATTERY) != 0;
		boolean mobileData = (reasonsDisabled & REASON_MOBILE_DATA) != 0;
		boolean location = (reasonsDisabled & REASON_COUNTRY_BLOCKED) != 0;

		StringBuilder s = new StringBuilder();
		if (location) {
			s.append("\t\u2022 ");
			s.append(activity.getString(R.string.tor_override_network_setting,
					viewModel.getCurrentCountryName()));
			s.append('\n');
		}
		if (mobileData) {
			s.append("\t\u2022 ");
			s.append(activity.getString(
					R.string.tor_override_mobile_data_setting));
			s.append('\n');
		}
		if (battery) {
			s.append("\t\u2022 ");
			s.append(activity.getString(R.string.tor_only_when_charging_title));
			s.append('\n');
		}
		String message = activity.getString(
				R.string.tor_override_settings_body, s.toString());

		AlertDialog.Builder b =
				new AlertDialog.Builder(activity, R.style.BriarDialogTheme);
		b.setTitle(R.string.tor_override_settings_title);
		b.setIcon(getDialogIcon(activity, R.drawable.ic_settings_black_24dp));
		b.setMessage(message);
		b.setPositiveButton(R.string.tor_override_settings_confirm,
				(dialog, which) ->
						viewModel.setTorEnabled(battery, mobileData, location));
		b.setNegativeButton(R.string.cancel, (dialog, which) ->
				dialog.dismiss());
		b.show();
	}
}
