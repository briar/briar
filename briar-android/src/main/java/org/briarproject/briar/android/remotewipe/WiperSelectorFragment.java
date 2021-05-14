package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contactselection.BaseContactSelectorAdapter;
import org.briarproject.briar.android.contactselection.ContactSelectorController;
import org.briarproject.briar.android.contactselection.ContactSelectorFragment;
import org.briarproject.briar.android.contactselection.SelectableContactItem;
import org.briarproject.briar.android.socialbackup.creation.CreateBackupController;

import javax.inject.Inject;

import androidx.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class WiperSelectorFragment extends ContactSelectorFragment {

	public static final String TAG = WiperSelectorFragment.class.getName();

	@Inject
	CreateBackupController controller;

	public static WiperSelectorFragment newInstance() {
		Bundle args = new Bundle();

		WiperSelectorFragment
				fragment = new WiperSelectorFragment();
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requireActivity().setTitle(R.string.title_select_wipers);
	}

	@Override
	protected ContactSelectorController<SelectableContactItem> getController() {
		return controller;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	protected void onSelectionChanged() {
		super.onSelectionChanged();
		if (menu == null) return;
		MenuItem item = menu.findItem(R.id.action_contacts_selected);
		if (item == null) return;

		BaseContactSelectorAdapter a = adapter;
		selectedContacts = a.getSelectedContactIds();

		int n = selectedContacts.size();
		int min = 2;
		boolean enough = n >= min;

		item.setVisible(enough);
		if (n == 0) {
			Toast.makeText(getContext(), String.format(getString(R.string.select_at_least_n_contacts), min),
					Toast.LENGTH_SHORT).show();
		} else if (n < min) {
			Toast.makeText(getContext(), String.format(getString(R.string.select_at_least_n_more_contacts), min - n),
					Toast.LENGTH_SHORT).show();
		}
	}

}
