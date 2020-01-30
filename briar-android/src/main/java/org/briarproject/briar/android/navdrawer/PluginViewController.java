package org.briarproject.briar.android.navdrawer;

import android.view.View;
import android.widget.ImageView;

import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;

import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.LifecycleOwner;

import static androidx.core.content.ContextCompat.getColor;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.briar.android.navdrawer.NavDrawerViewModel.TRANSPORT_IDS;

class PluginViewController {

	private final ImageView torIcon, wifiIcon, btIcon;
	private final SwitchCompat torSwitch, wifiSwitch, btSwitch;

	PluginViewController(View v, LifecycleOwner owner,
			NavDrawerViewModel viewModel) {

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

}
