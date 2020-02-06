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

import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.LifecycleOwner;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.core.content.ContextCompat.getColor;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.briar.android.navdrawer.NavDrawerViewModel.TRANSPORT_IDS;

class PluginViewController {

	private final ImageView torIconExpanded, torIconCollapsed;
	private final ImageView wifiIconExpanded, wifiIconCollapsed;
	private final ImageView btIconExpanded, btIconCollapsed;
	private final SwitchCompat torSwitch, wifiSwitch, btSwitch;

	PluginViewController(View v, LifecycleOwner owner,
			NavDrawerViewModel viewModel) {

		ScrollView scrollView = v.findViewById(R.id.drawerScrollView);
		View expandedLayout = v.findViewById(R.id.expandedLayout);
		View collapsedLayout = v.findViewById(R.id.collapsedLayout);

		expandedLayout.addOnLayoutChangeListener((view, left, top, right,
				bottom, oldLeft, oldTop, oldRight, oldBottom) ->
				scrollView.fullScroll(FOCUS_DOWN));

		collapsedLayout.setOnClickListener(view -> {
			expandedLayout.setVisibility(VISIBLE);
			collapsedLayout.setVisibility(GONE);
		});

		v.findViewById(R.id.chevronViewExpanded).setOnClickListener(view -> {
			expandedLayout.setVisibility(GONE);
			collapsedLayout.setVisibility(VISIBLE);
		});

		torIconExpanded = v.findViewById(R.id.torIconExpanded);
		torIconCollapsed = v.findViewById(R.id.torIconCollapsed);
		wifiIconExpanded = v.findViewById(R.id.wifiIconExpanded);
		wifiIconCollapsed = v.findViewById(R.id.wifiIconCollapsed);
		btIconExpanded = v.findViewById(R.id.btIconExpanded);
		btIconCollapsed = v.findViewById(R.id.btIconCollapsed);

		torSwitch = v.findViewById(R.id.torSwitch);
		wifiSwitch = v.findViewById(R.id.wifiSwitch);
		btSwitch = v.findViewById(R.id.btSwitch);

		for (TransportId t : TRANSPORT_IDS) {
			// a OnCheckedChangeListener would get triggered on programmatic updates
			SwitchCompat switchCompat = getSwitch(t);
			switchCompat.setOnClickListener(buttonView -> {
				// TODO check reason first and change settings if needed
				viewModel.setPluginEnabled(t, switchCompat.isChecked());
				// Revert the switch to its previous state until the plugin
				// changes its state
				switchCompat.toggle();
			});
			viewModel.getPluginState(t)
					.observe(owner, state -> stateUpdate(t, state));
		}
	}

	private void stateUpdate(TransportId id, State state) {
		updateIcon(getExpandedIcon(id), state);
		updateIcon(getCollapsedIcon(id), state);
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

	private ImageView getExpandedIcon(TransportId id) {
		if (id == TorConstants.ID) return torIconExpanded;
		if (id == BluetoothConstants.ID) return btIconExpanded;
		if (id == LanTcpConstants.ID) return wifiIconExpanded;
		throw new AssertionError();
	}

	private ImageView getCollapsedIcon(TransportId id) {
		if (id == TorConstants.ID) return torIconCollapsed;
		if (id == BluetoothConstants.ID) return btIconCollapsed;
		if (id == LanTcpConstants.ID) return wifiIconCollapsed;
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

}
