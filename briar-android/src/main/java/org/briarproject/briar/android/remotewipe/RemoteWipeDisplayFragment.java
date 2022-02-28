package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

		View contentView = inflater.inflate(R.layout.list, container, false);

		viewModel.getWiperContactIds();
		list = contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(R.string.no_contacts);

		viewModel.getContactListItems().observe(getViewLifecycleOwner(),
				result -> result.onError(this::handleException)
						.onSuccess(adapter::submitList)
		);

		return contentView;
	}
//		View view = inflater.inflate(R.layout.fragment_display_remote_wipe,
//				container, false);
//        List<String> wiperNames = viewModel.getWiperNames();
//		StringBuilder custodianNamesString = new StringBuilder();
//		for (String custodianName : wiperNames) {
//			custodianNamesString
//					.append("• ")
//					.append(custodianName)
//					.append("\n");
//		}
//		TextView textViewThreshold = view.findViewById(R.id.textViewWipers);
//		textViewThreshold.setText(custodianNamesString.toString());
//
//		Button button = view.findViewById(R.id.button);
//		button.setOnClickListener(e -> viewModel.onModifyWipers());
//		return view;

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
}
