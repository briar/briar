package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.android.sharing.ShareActivity.CONTACTS;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIds;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

abstract class ShareMessageFragment extends BaseFragment {

	public final static String TAG = ShareMessageFragment.class.getName();

	protected static final Logger LOG = Logger.getLogger(TAG);

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
		try {
			shareActivity = (ShareActivity) context;
		} catch (ClassCastException e) {
			throw new InstantiationError(
					"This fragment is only meant to be attached to the ShareForumActivity");
		}
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
		ui.button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onButtonClick();
			}
		});

		return v;
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

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	protected void setTitle(int res) {
		shareActivity.setTitle(res);
	}

	private void onButtonClick() {
		// disable button to prevent accidental double invitations
		ui.button.setEnabled(false);

		String msg = ui.message.getText().toString();
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

	protected void runOnUiThread(Runnable runnable) {
		listener.runOnUiThread(runnable);
	}

	protected static class ViewHolder {
		protected final EditText message;
		protected final Button button;

		ViewHolder(View v) {
			message = (EditText) v.findViewById(R.id.invitationMessageView);
			button = (Button) v.findViewById(R.id.shareForumButton);
		}
	}
}
