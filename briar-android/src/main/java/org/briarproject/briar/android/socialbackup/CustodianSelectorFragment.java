package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.socialbackup.creation.CreateBackupController;
import org.briarproject.briar.android.contactselection.BaseContactSelectorAdapter;
import org.briarproject.briar.android.contactselection.ContactSelectorController;
import org.briarproject.briar.android.contactselection.ContactSelectorFragment;
import org.briarproject.briar.android.contactselection.SelectableContactItem;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CustodianSelectorFragment extends ContactSelectorFragment {

	public static final String TAG = CustodianSelectorFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SocialBackupSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
	}

	@Inject
	CreateBackupController controller;

	public static CustodianSelectorFragment newInstance() {
		Bundle args = new Bundle();

		CustodianSelectorFragment fragment = new CustodianSelectorFragment();
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requireActivity().setTitle(R.string.title_select_custodians);
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
		int max = 7;
		boolean amountIsValid = (n >= min) && (n <= max);

		item.setVisible(amountIsValid);
		if (n == 0) {
			Toast.makeText(getContext(), String.format(getString(R.string.select_at_least_n_contacts), min),
					Toast.LENGTH_SHORT).show();
		} else if (n < min) {
			Toast.makeText(getContext(), String.format(getString(R.string.select_at_least_n_more_contacts), min - n),
					Toast.LENGTH_SHORT).show();
		} else if (n > max) {
			Toast.makeText(getContext(), String.format(getString(R.string.select_no_more_than_n_contacts), max),
					Toast.LENGTH_SHORT).show();
		}
	}

}
