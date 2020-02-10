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

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.LifecycleOwner;

import static android.os.Build.VERSION.SDK_INT;
import static android.transition.TransitionManager.beginDelayedTransition;
import static android.view.View.FOCUS_DOWN;
import static androidx.core.content.ContextCompat.getColor;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.briar.android.navdrawer.NavDrawerViewModel.TRANSPORT_IDS;

class PluginViewController {

	private final ConstraintLayout drawerContent;
	private final ConstraintSet collapsedConstraints, expandedConstraints;
	private final AppCompatImageButton chevronView;
	private final ImageView torIcon, wifiIcon, btIcon;
	private final SwitchCompat torSwitch, wifiSwitch, btSwitch;

	private boolean expanded = false;

	PluginViewController(View v, LifecycleOwner owner,
			NavDrawerViewModel viewModel) {
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
