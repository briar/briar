package org.briarproject.android.contactselection;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.android.contact.ContactItemViewHolder;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;

import static org.briarproject.android.contactselection.ContactSelectorActivity.CONTACTS;
import static org.briarproject.android.contactselection.ContactSelectorActivity.getContactsFromIds;
import static org.briarproject.android.contactselection.ContactSelectorActivity.getContactsFromIntegers;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class BaseContactSelectorFragment<I extends SelectableContactItem, H extends ContactItemViewHolder<I>>
		extends BaseFragment
		implements OnContactClickListener<I> {

	protected BriarRecyclerView list;
	protected BaseContactSelectorAdapter<I, H> adapter;
	protected Collection<ContactId> selectedContacts = new ArrayList<>();
	protected ContactSelectorListener<I> listener;

	private GroupId groupId;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ContactSelectorListener<I>) context;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);
	}

	@Override
	@CallSuper
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		View contentView = inflater.inflate(R.layout.list, container, false);

		if (Build.VERSION.SDK_INT >= 21) {
			setExitTransition(new Fade());
		}

		list = (BriarRecyclerView) contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setEmptyText(getString(R.string.no_contacts_selector));

		// restore selected contacts if available
		if (savedInstanceState != null) {
			ArrayList<Integer> intContacts =
					savedInstanceState.getIntegerArrayList(CONTACTS);
			if (intContacts != null) {
				selectedContacts = getContactsFromIntegers(intContacts);
			}
		}
		return contentView;
	}

	@Override
	public void onStart() {
		super.onStart();
		loadContacts(selectedContacts);
	}

	@Override
	public void onStop() {
		super.onStop();
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (adapter != null) {
			selectedContacts = adapter.getSelectedContactIds();
			outState.putIntegerArrayList(CONTACTS,
					getContactsFromIds(selectedContacts));
		}
	}

	@Override
	public void onItemClick(View view, I item) {
		item.toggleSelected();
		adapter.notifyItemChanged(adapter.findItemPosition(item), item);
		onSelectionChanged();
	}

	private void loadContacts(final Collection<ContactId> selection) {
		getController().loadContacts(groupId, selection,
				new UiResultExceptionHandler<Collection<I>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<I> contacts) {
						if (contacts.isEmpty()) list.showData();
						else adapter.addAll(contacts);
						onSelectionChanged();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO error handling
						finish();
					}
				});
	}

	protected abstract void onSelectionChanged();

	protected abstract ContactSelectorController<I> getController();

}
