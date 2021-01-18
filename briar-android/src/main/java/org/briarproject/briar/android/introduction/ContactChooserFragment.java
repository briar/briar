package org.briarproject.briar.android.introduction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.ContactListAdapter;
import org.briarproject.briar.android.contact.ContactListItem;
import org.briarproject.briar.android.contact.OnContactClickListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.BriarRecyclerView;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactChooserFragment extends BaseFragment
		implements OnContactClickListener<ContactListItem> {

	private static final String TAG = ContactChooserFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ContactListViewModel viewModel;
	private final ContactListAdapter adapter = new ContactListAdapter(this);
	private BriarRecyclerView list;
	private ContactId contactId;

	static ContactChooserFragment newInstance(ContactId id) {
		Bundle args = new Bundle();

		ContactChooserFragment fragment = new ContactChooserFragment();
		args.putInt(CONTACT_ID, id.getInt());
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ContactListViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		View contentView = inflater.inflate(R.layout.list, container, false);

		list = contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(R.string.no_contacts);

		contactId = new ContactId(requireArguments().getInt(CONTACT_ID));
		viewModel.setContactId(contactId);

		viewModel.loadContacts();

		viewModel.getContactListItems().observe(getViewLifecycleOwner(),
				result -> result.onError(this::handleException)
						.onSuccess(adapter::submitList)
		);

		return contentView;
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

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void showMessageScreen(Contact other) {
		IntroductionMessageFragment messageFragment =
				IntroductionMessageFragment.newInstance(contactId.getInt(),
						other.getId().getInt());
		showNextFragment(messageFragment);
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
		showMessageScreen(item.getContact());
	}
}
