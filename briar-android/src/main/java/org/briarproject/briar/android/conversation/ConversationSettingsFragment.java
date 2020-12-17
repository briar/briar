package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.os.Bundle;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.briar.R;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

public class ConversationSettingsFragment extends PreferenceFragmentCompat
		implements Preference.OnPreferenceChangeListener {

	private static final String DM_ENABLE = "pref_key_disappearing_messages";
	private static final String DM_EXPLANATION =
			"pref_key_disappearing_messages_explanation";
	private static final String DM_LEARN_MORE =
			"pref_key_disappearing_messages_learn_more";

	private static final Logger LOG =
			Logger.getLogger(ConversationSettingsFragment.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Inject
	@DatabaseExecutor
	Executor dbExecutor;

	@Inject
	TransactionManager db;

	@Inject
	AutoDeleteManager autoDeleteManager;

	private ConversationSettingsActivity listener;

	private SwitchPreference enableDisappearingMessages;

	private volatile boolean disappearingMessages = false;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ConversationSettingsActivity) context;
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.conversation_settings);

		enableDisappearingMessages = findPreference(DM_ENABLE);

		enableDisappearingMessages.setOnPreferenceChangeListener(this);

		viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)
				.get(ConversationViewModel.class);
	}

	private ConversationViewModel viewModel;

	@Override
	public void onStart() {
		super.onStart();
		setSettingsEnabled(false);
		loadSettings();
	}

	private void setSettingsEnabled(boolean enabled) {
		enableDisappearingMessages.setEnabled(enabled);
	}

	private void loadSettings() {
		observeOnce(viewModel.getContact(), this, c -> {
			dbExecutor.execute(() -> {
				try {
					db.transaction(false, txn -> {
						long timer = autoDeleteManager
								.getAutoDeleteTimer(txn, c.getId());
						disappearingMessages = timer != NO_AUTO_DELETE_TIMER;
					});
					displaySettings();
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			});
		});
	}

	private void displaySettings() {
		listener.runOnUiThreadUnlessDestroyed(() -> {
			enableDisappearingMessages.setChecked(disappearingMessages);
			setSettingsEnabled(true);
		});
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == enableDisappearingMessages) {
			boolean dmSetting = (Boolean) newValue;
			viewModel.setAutoDeleteTimerEnabled(dmSetting);
		}
		return true;
	}

}
