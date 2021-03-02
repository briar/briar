package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.socialbackup.creation.CreateBackupController;
import org.briarproject.briar.android.contact.BaseContactListAdapter;
import org.briarproject.briar.android.contactselection.BaseContactSelectorFragment;
import org.briarproject.briar.android.contactselection.ContactDisplayAdapter;
import org.briarproject.briar.android.contactselection.ContactSelectorController;
import org.briarproject.briar.android.contactselection.SelectableContactItem;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CustodianDisplayFragment extends BaseContactSelectorFragment<SelectableContactItem, ContactDisplayAdapter> {

	public static final String TAG = CustodianDisplayFragment.class.getName();

	@Inject
	CreateBackupController controller;

	public static CustodianDisplayFragment newInstance() {
		Bundle args = new Bundle();
		args.putInt(BaseContactSelectorFragment.ARG_LAYOUT, R.layout.list_with_headline);
		CustodianDisplayFragment fragment = new CustodianDisplayFragment();
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	protected ContactDisplayAdapter getAdapter(Context context,
			BaseContactListAdapter.OnContactClickListener<SelectableContactItem> listener) {
		return new ContactDisplayAdapter(context, listener);
	}

	@Override
	protected void onSelectionChanged() {

	}

	public View onCreateView(LayoutInflater inflater,
			@javax.annotation.Nullable ViewGroup container,
			@javax.annotation.Nullable Bundle savedInstanceState) {

		View view = super.onCreateView(inflater, container, savedInstanceState);
		TextView headline = view.findViewById(R.id.headline);
		headline.setText(R.string.backup_created);
		return view;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.activity_name_distributed_backup);
	}

	@Override
	protected ContactSelectorController<SelectableContactItem> getController() {
		controller.setMax(5);
		return controller;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
