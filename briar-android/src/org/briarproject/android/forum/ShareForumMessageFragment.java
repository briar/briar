package org.briarproject.android.forum;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.ShareForumActivity.CONTACTS;
import static org.briarproject.android.forum.ShareForumActivity.getContactsFromIds;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;

public class ShareForumMessageFragment extends BaseFragment {

	public final static String TAG = "IntroductionMessageFragment";

	private static final Logger LOG =
			Logger.getLogger(ShareForumMessageFragment.class.getName());

	private ShareForumActivity shareForumActivity;
	private ViewHolder ui;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;
	private volatile GroupId groupId;
	private volatile Collection<ContactId> contacts;

	public void initBundle(GroupId groupId, Collection<ContactId> contacts) {
		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, groupId.getBytes());
		bundle.putIntegerArrayList(CONTACTS, getContactsFromIds(contacts));
		setArguments(bundle);
	}

	@Inject
	public ShareForumMessageFragment() {

	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			shareForumActivity = (ShareForumActivity) context;
		} catch (ClassCastException e) {
			throw new InstantiationError(
					"This fragment is only meant to be attached to the ShareForumActivity");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		// change toolbar text
		ActionBar actionBar = shareForumActivity.getSupportActionBar();
		if (actionBar != null) {
			actionBar.setTitle(R.string.forum_share_button);
		}

		// allow for home button to act as back button
		setHasOptionsMenu(true);

		// inflate view
		View v = inflater.inflate(R.layout.share_forum_message, container,
				false);
		ui = new ViewHolder(v);
		ui.button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onButtonClick();
			}
		});

		// get groupID and contactIDs from fragment arguments
		groupId = new GroupId(getArguments().getByteArray(GROUP_ID));
		ArrayList<Integer> intContacts =
				getArguments().getIntegerArrayList(CONTACTS);
		if (intContacts == null) throw new IllegalArgumentException();
		contacts = ShareForumActivity.getContactsFromIntegers(intContacts);

		return v;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				shareForumActivity.onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	public void onButtonClick() {
		// disable button to prevent accidental double invitations
		ui.button.setEnabled(false);

		String msg = ui.message.getText().toString();
		shareForum(msg);

		// don't wait for the introduction to be made before finishing activity
		shareForumActivity.sharingSuccessful(ui.message);
	}

	private void shareForum(final String msg) {
		shareForumActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : contacts) {
						forumSharingManager.sendForumInvitation(groupId, c,
								msg);
					}
				} catch (DbException e) {
					sharingError();
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void sharingError() {
		shareForumActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(shareForumActivity,
						R.string.introduction_error, LENGTH_SHORT).show();
			}
		});
	}

	private static class ViewHolder {

		private final EditText message;
		private final Button button;

		ViewHolder(View v) {
			message = (EditText) v.findViewById(R.id.invitationMessageView);
			button = (Button) v.findViewById(R.id.shareForumButton);
		}
	}
}
