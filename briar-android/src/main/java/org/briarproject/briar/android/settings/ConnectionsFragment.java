package org.briarproject.briar.android.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.settings.SettingsActivity.enableAndPersist;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ConnectionsFragment extends PreferenceFragmentCompat {

	static final String PREF_KEY_BLUETOOTH = "pref_key_bluetooth";
	static final String PREF_KEY_WIFI = "pref_key_wifi";
	static final String PREF_KEY_TOR_ENABLE = "pref_key_tor_enable";
	static final String PREF_KEY_TOR_NETWORK = "pref_key_tor_network";
	static final String PREF_KEY_TOR_MOBILE_DATA =
			"pref_key_tor_mobile_data";
	static final String PREF_KEY_TOR_ONLY_WHEN_CHARGING =
			"pref_key_tor_only_when_charging";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SettingsViewModel viewModel;
	private ConnectionsManager connectionsManager;

	private SwitchPreference enableBluetooth;
	private SwitchPreference enableWifi;
	private SwitchPreference enableTor;
	private ListPreference torNetwork;
	private SwitchPreference torMobile;
	private SwitchPreference torOnlyWhenCharging;

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		getAndroidComponent(context).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SettingsViewModel.class);
		connectionsManager = viewModel.connectionsManager;
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings_connections);

		enableBluetooth = findPreference(PREF_KEY_BLUETOOTH);
		enableWifi = findPreference(PREF_KEY_WIFI);
		enableTor = findPreference(PREF_KEY_TOR_ENABLE);
		torNetwork = findPreference(PREF_KEY_TOR_NETWORK);
		torMobile = findPreference(PREF_KEY_TOR_MOBILE_DATA);
		torOnlyWhenCharging = findPreference(PREF_KEY_TOR_ONLY_WHEN_CHARGING);

		torNetwork.setSummaryProvider(viewModel.torSummaryProvider);

		enableBluetooth.setPreferenceDataStore(connectionsManager.btStore);
		enableWifi.setPreferenceDataStore(connectionsManager.wifiStore);
		enableTor.setPreferenceDataStore(connectionsManager.torStore);
		torNetwork.setPreferenceDataStore(connectionsManager.torStore);
		torMobile.setPreferenceDataStore(connectionsManager.torStore);
		torOnlyWhenCharging.setPreferenceDataStore(connectionsManager.torStore);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// persist changes after setting initial value and enabling
		LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
		connectionsManager.btEnabled().observe(lifecycleOwner, enabled -> {
			enableBluetooth.setChecked(enabled);
			enableAndPersist(enableBluetooth);
		});
		connectionsManager.wifiEnabled().observe(lifecycleOwner, enabled -> {
			enableWifi.setChecked(enabled);
			enableAndPersist(enableWifi);
		});
		connectionsManager.torEnabled().observe(lifecycleOwner, enabled -> {
			enableTor.setChecked(enabled);
			enableAndPersist(enableTor);
		});
		connectionsManager.torNetwork().observe(lifecycleOwner, value -> {
			torNetwork.setValue(value);
			enableAndPersist(torNetwork);
		});
		connectionsManager.torMobile().observe(lifecycleOwner, enabled -> {
			torMobile.setChecked(enabled);
			enableAndPersist(torMobile);
		});
		connectionsManager.torCharging().observe(lifecycleOwner, enabled -> {
			torOnlyWhenCharging.setChecked(enabled);
			enableAndPersist(torOnlyWhenCharging);
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.network_settings_title);
	}

}
