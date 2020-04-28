package org.briarproject.briar.android.navdrawer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TransportsActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private final List<Transport> transports = new ArrayList<>(3);

	private PluginViewModel viewModel;
	private BaseAdapter transportsAdapter;

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_transports);

		ViewModelProvider provider =
				ViewModelProviders.of(this, viewModelFactory);
		viewModel = provider.get(PluginViewModel.class);

		GridView grid = findViewById(R.id.grid);
		initializeCards();
		grid.setAdapter(transportsAdapter);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		} else if (item.getItemId() == R.id.action_open_settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.transports_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	private void initializeCards() {
		transportsAdapter = new BaseAdapter() {

			@Override
			public int getCount() {
				return transports.size();
			}

			@Override
			public Transport getItem(int position) {
				return transports.get(position);
			}

			@Override
			public long getItemId(int position) {
				return 0;
			}

			@Override
			public View getView(int position, View convertView,
					ViewGroup parent) {
				View view;
				if (convertView != null) {
					view = convertView;
				} else {
					LayoutInflater inflater = getLayoutInflater();
					view = inflater.inflate(R.layout.list_item_transport_card,
							parent, false);
				}

				Transport t = getItem(position);

				ImageView icon = view.findViewById(R.id.icon);
				icon.setImageDrawable(ContextCompat.getDrawable(
						TransportsActivity.this, t.iconDrawable));
				icon.setColorFilter(ContextCompat.getColor(
						TransportsActivity.this, t.iconColor));

				TextView title = view.findViewById(R.id.title);
				title.setText(getString(t.title));

				SwitchCompat switchCompat =
						view.findViewById(R.id.switchCompat);
				switchCompat.setText(getString(t.switchLabel));
				switchCompat.setOnClickListener(v ->
						viewModel.enableTransport(t.id,
								switchCompat.isChecked()));
				switchCompat.setChecked(t.isSwitchChecked);

				TextView deviceStatus = view.findViewById(R.id.deviceStatus);
				deviceStatus.setText(getBulletString(t.deviceStatus));

				TextView appStatus = view.findViewById(R.id.appStatus);
				appStatus.setText(getBulletString(t.pluginStatus));

				return view;
			}
		};

		Transport tor = createTransport(TorConstants.ID,
				R.drawable.transport_tor, R.string.transport_tor,
				R.string.tor_enable_title, R.string.tor_device_status_offline,
				R.string.tor_plugin_status_inactive);
		transports.add(tor);

		Transport wifi = createTransport(LanTcpConstants.ID,
				R.drawable.transport_lan, R.string.transport_lan_long,
				R.string.wifi_setting, R.string.lan_device_status_off,
				R.string.lan_plugin_status_inactive);
		transports.add(wifi);

		Transport bt = createTransport(BluetoothConstants.ID,
				R.drawable.transport_bt, R.string.transport_bt,
				R.string.bluetooth_setting, R.string.bt_device_status_off,
				R.string.bt_plugin_status_inactive);
		transports.add(bt);

		viewModel.getNetworkStatus().observe(this, status -> {
			tor.deviceStatus = getTorDeviceStatus(status);
			wifi.deviceStatus = getWifiDeviceStatus(status);
			transportsAdapter.notifyDataSetChanged();
		});

		viewModel.getBluetoothTurnedOn().observe(this, on -> {
			bt.deviceStatus = getBtDeviceStatus(on);
			transportsAdapter.notifyDataSetChanged();
		});
	}

	private String getBulletString(@StringRes int resId) {
		return "\u2022 " + getString(resId);
	}

	@ColorRes
	private int getIconColor(State state) {
		if (state == ACTIVE) return R.color.briar_lime_400;
		else if (state == ENABLING) return R.color.briar_orange_500;
		else return android.R.color.tertiary_text_light;
	}

	@StringRes
	private int getTorDeviceStatus(NetworkStatus status) {
		if (!status.isConnected()) return R.string.tor_device_status_offline;
		if (status.isWifi()) return R.string.tor_device_status_online_wifi;
		else return R.string.tor_device_status_online_mobile_data;
	}

	@StringRes
	private int getWifiDeviceStatus(NetworkStatus status) {
		if (status.isWifi()) return R.string.lan_device_status_on;
		else return R.string.lan_device_status_off;
	}

	@StringRes
	private int getBtDeviceStatus(boolean on) {
		if (on) return R.string.bt_device_status_on;
		else return R.string.bt_device_status_off;
	}

	@StringRes
	private int getPluginStatus(TransportId id, State state) {
		if (id.equals(TorConstants.ID)) {
			return getTorPluginStatus(state);
		} else if (id.equals(LanTcpConstants.ID)) {
			return getWifiPluginStatus(state);
		} else if (id.equals(BluetoothConstants.ID)) {
			return getBtPluginStatus(state);
		} else throw new AssertionError();
	}

	@StringRes
	private int getTorPluginStatus(State state) {
		if (state == ENABLING) return R.string.tor_plugin_status_enabling;
		else if (state == ACTIVE) return R.string.tor_plugin_status_active;
		else if (state == DISABLED) return R.string.tor_plugin_status_disabled;
		else return R.string.tor_plugin_status_inactive;
	}

	@StringRes
	private int getWifiPluginStatus(State state) {
		if (state == ENABLING) return R.string.lan_plugin_status_enabling;
		else if (state == ACTIVE) return R.string.lan_plugin_status_active;
		else if (state == DISABLED) return R.string.lan_plugin_status_disabled;
		else return R.string.lan_plugin_status_inactive;
	}

	@StringRes
	private int getBtPluginStatus(State state) {
		if (state == ENABLING) return R.string.bt_plugin_status_enabling;
		else if (state == ACTIVE) return R.string.bt_plugin_status_active;
		else if (state == DISABLED) return R.string.bt_plugin_status_disabled;
		else return R.string.bt_plugin_status_inactive;
	}

	private Transport createTransport(TransportId id,
			@DrawableRes int iconDrawable, @StringRes int title,
			@StringRes int switchLabel, @StringRes int deviceStatus,
			@StringRes int pluginStatus) {
		int iconColor = getIconColor(STARTING_STOPPING);
		Transport transport = new Transport(id, iconDrawable, iconColor, title,
				switchLabel, false, deviceStatus, pluginStatus);
		viewModel.getPluginState(id).observe(this, state -> {
			transport.iconColor = getIconColor(state);
			transport.pluginStatus = getPluginStatus(transport.id, state);
			transportsAdapter.notifyDataSetChanged();
		});
		viewModel.getPluginEnabledSetting(id).observe(this, enabled -> {
			transport.isSwitchChecked = enabled;
			transportsAdapter.notifyDataSetChanged();
		});
		return transport;
	}

	private static class Transport {

		private final TransportId id;

		@DrawableRes
		private final int iconDrawable;
		@StringRes
		private final int title, switchLabel;

		@ColorRes
		private int iconColor;
		@StringRes
		private int deviceStatus, pluginStatus;
		private boolean isSwitchChecked;

		private Transport(TransportId id, @DrawableRes int iconDrawable,
				@ColorRes int iconColor, @StringRes int title,
				@StringRes int switchLabel, boolean isSwitchChecked,
				@StringRes int deviceStatus, @StringRes int pluginStatus) {
			this.id = id;
			this.iconDrawable = iconDrawable;
			this.iconColor = iconColor;
			this.title = title;
			this.switchLabel = switchLabel;
			this.isSwitchChecked = isSwitchChecked;
			this.deviceStatus = deviceStatus;
			this.pluginStatus = pluginStatus;
		}
	}
}
