package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.LargeTextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.android.sharing.ShareActivity.CONTACTS;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIds;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;
import static org.briarproject.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;

abstract class ShareMessageFragment extends BaseFragment
		implements TextInputListener {

	protected ViewHolder ui;
	private ShareActivity shareActivity;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;
	@Inject
	protected volatile BlogSharingManager blogSharingManager;
	private volatile GroupId groupId;
	private volatile Collection<ContactId> contacts;

	protected static Bundle getArguments(GroupId groupId,
			Collection<ContactId> contacts) {

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		args.putIntegerArrayList(CONTACTS, getContactsFromIds(contacts));
		return args;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		shareActivity = (ShareActivity) context;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		// allow for "up" button to act as back button
		setHasOptionsMenu(true);

		// get groupID and contactIDs from fragment arguments
		groupId = new GroupId(getArguments().getByteArray(GROUP_ID));
		ArrayList<Integer> intContacts =
				getArguments().getIntegerArrayList(CONTACTS);
		if (intContacts == null) throw new IllegalArgumentException();
		contacts = ShareActivity.getContactsFromIntegers(intContacts);

		// inflate view
		View v = inflater.inflate(R.layout.fragment_share_message, container,
				false);
		ui = new ViewHolder(v);
		ui.message.setListener(this);

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		ui.message.showSoftKeyboard();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				shareActivity.onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	protected void setTitle(int res) {
		shareActivity.setTitle(res);
	}

	@Override
	public void onSendClick(String msg) {
		// disable button to prevent accidental double invitations
		ui.message.setSendButtonEnabled(false);

		msg = StringUtils.truncateUtf8(msg, MAX_INVITATION_MESSAGE_LENGTH);
		share(msg);

		// don't wait for the invitation to be made before finishing activity
		shareActivity.sharingSuccessful(ui.message);
	}

	abstract void share(final String msg);

	abstract void sharingError();

	protected Collection<ContactId> getContacts() {
		return contacts;
	}

	protected GroupId getGroupId() {
		return groupId;
	}

	protected static class ViewHolder {
		protected final LargeTextInputView message;

		private ViewHolder(View v) {
			message = (LargeTextInputView) v
					.findViewById(R.id.invitationMessageView);
		}
	}
}
