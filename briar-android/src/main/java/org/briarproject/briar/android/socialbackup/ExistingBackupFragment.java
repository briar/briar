package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.ContactListAdapter;
import org.briarproject.briar.android.contact.ContactListItem;
import org.briarproject.briar.android.contact.OnContactClickListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.BriarRecyclerView;

import java.util.Arrays;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

public class ExistingBackupFragment extends BaseFragment implements
		OnContactClickListener<ContactListItem> {

	public static final String TAG = ExistingBackupFragment.class.getName();
	private final ContactListAdapter adapter = new ContactListAdapter(this);
	private BriarRecyclerView list;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SocialBackupSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_distributed_backup);

	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		// change toolbar text (relevant when navigating back to this fragment)
		requireActivity().setTitle(R.string.social_backup_trusted_contacts);

		View view = inflater.inflate(R.layout.fragment_existing_backup,
				container, false);
		viewModel.loadCustodianList();
		list = view.findViewById(R.id.custodianList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(R.string.no_contacts);

		viewModel.getContactListItems().observe(getViewLifecycleOwner(),
				result -> result.onError(this::handleException)
						.onSuccess(adapter::submitList)
		);

		int threshold = viewModel.getThresholdFromExistingBackup();
		int numberOfCustodians =
				viewModel.getNumberOfCustodiansFromExistingBackup();

		TextView mOfn = view.findViewById(R.id.textViewThreshold);
		mOfn.setText(String.format(
				getString(R.string.threshold_m_of_n), threshold,
				numberOfCustodians));

		TextView thresholdRepresentation =
				view.findViewById(R.id.textViewThresholdRepresentation);
		thresholdRepresentation.setText(
				buildThresholdRepresentationString(threshold,
						numberOfCustodians));
		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
	}

	private SpannableStringBuilder buildThresholdRepresentationString(
			int threshold, int numberOfCustodians) {
		char[] charArray = new char[numberOfCustodians];
		Arrays.fill(charArray, ' ');
		SpannableStringBuilder string =
				new SpannableStringBuilder(new String(charArray));

		for (int i = 0; i < numberOfCustodians; i++) {
			int drawable = i < threshold
					? R.drawable.ic_custodian_required
					: R.drawable.ic_custodian_optional;
			string.setSpan(new ImageSpan(getContext(), drawable), i,
					i + 1,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		// If we have more than 6, split it on two lines
		if (numberOfCustodians > 6) string.insert(4, "\n");
		return string;
	}
}
