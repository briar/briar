package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.ContactListAdapter;
import org.briarproject.briar.android.contact.ContactListItem;
import org.briarproject.briar.android.contact.OnContactClickListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.BriarRecyclerView;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

public class RemoteWipeDisplayFragment extends BaseFragment
		implements OnContactClickListener<ContactListItem> {

	public static final String TAG = RemoteWipeDisplayFragment.class.getName();

	private final ContactListAdapter adapter = new ContactListAdapter(this);
	private BriarRecyclerView list;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private RemoteWipeSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(RemoteWipeSetupViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		// change toolbar text (relevant when navigating back to this fragment)
		requireActivity().setTitle(R.string.assigned_wipers);

		View contentView = inflater.inflate(R.layout.fragment_remote_wipe_display, container, false);

		viewModel.getWiperContactIds();
		list = contentView.findViewById(R.id.wiperList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(R.string.no_contacts);

		viewModel.getContactListItems().observe(getViewLifecycleOwner(),
				result -> result.onError(this::handleException)
						.onSuccess(adapter::submitList)
		);

		Button changeWipersButton = contentView.findViewById(R.id.button_change);
		changeWipersButton.setOnClickListener(e -> viewModel.onModifyWipers());
		
		Button disableRemoteWipeButton = contentView.findViewById(R.id.button_cancel);
		disableRemoteWipeButton.setOnClickListener(e -> viewModel.onDisableRemoteWipe());

		viewModel.getState().observe(getViewLifecycleOwner(), this::onStateChanged);
		return contentView;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
	}

	@Override
	public void onStart() {
		super.onStart();
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		list.stopPeriodicUpdate();
	}

	private void onStateChanged(RemoteWipeSetupState state) {
		if (state.equals(RemoteWipeSetupState.DISABLED)) {
			showDisabledDialog();
		}
	}

	private void showDisabledDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.remote_wipe_disable_success);
		builder.setPositiveButton(R.string.ok,
				(dialog, which) -> viewModel.onSuccessDismissed());
		builder.setIcon(R.drawable.ic_baseline_done_outline_24);
		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
