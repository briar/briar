package org.briarproject.android.sharing;

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
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogSharingManager;
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
import static org.briarproject.android.sharing.ShareActivity.BLOG;
import static org.briarproject.android.sharing.ShareActivity.CONTACTS;
import static org.briarproject.android.sharing.ShareActivity.FORUM;
import static org.briarproject.android.sharing.ShareActivity.SHAREABLE;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIds;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

public class ShareMessageFragment extends BaseFragment {

	public final static String TAG = "IntroductionMessageFragment";

	private static final Logger LOG =
			Logger.getLogger(ShareMessageFragment.class.getName());

	private ShareActivity shareActivity;
	private ViewHolder ui;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;
	@Inject
	protected volatile BlogSharingManager blogSharingManager;
	private volatile GroupId groupId;
	private volatile int shareable;
	private volatile Collection<ContactId> contacts;

	public static ShareMessageFragment newInstance(int shareable,
			GroupId groupId, Collection<ContactId> contacts) {

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		args.putInt(SHAREABLE, shareable);
		args.putIntegerArrayList(CONTACTS, getContactsFromIds(contacts));
		ShareMessageFragment fragment = new ShareMessageFragment();
		fragment.setArguments(args);
		return fragment;
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

		// allow for home button to act as back button
		setHasOptionsMenu(true);

		// get groupID, shareable type and contactIDs from fragment arguments
		groupId = new GroupId(getArguments().getByteArray(GROUP_ID));
		shareable = getArguments().getInt(SHAREABLE);
		ArrayList<Integer> intContacts =
				getArguments().getIntegerArrayList(CONTACTS);
		if (intContacts == null) throw new IllegalArgumentException();
		contacts = ShareActivity.getContactsFromIntegers(intContacts);

		// change toolbar text
		ActionBar actionBar = shareActivity.getSupportActionBar();
		if (actionBar != null) {
			if (shareable == FORUM) {
				actionBar.setTitle(R.string.forum_share_button);
			} else if (shareable == BLOG) {
				actionBar.setTitle(R.string.blogs_sharing_button);
			} else {
				throw new IllegalArgumentException("Invalid Shareable Type!");
			}
		}

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
		if (shareable == BLOG) {
			ui.button.setText(getString(R.string.blogs_sharing_button));
		}

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

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private void onButtonClick() {
		// disable button to prevent accidental double invitations
		ui.button.setEnabled(false);

		String msg = ui.message.getText().toString();
		shareForum(msg);

		// don't wait for the introduction to be made before finishing activity
		shareActivity.sharingSuccessful(ui.message);
	}

	private void shareForum(final String msg) {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : contacts) {
						if (shareable == FORUM) {
							forumSharingManager.sendInvitation(groupId, c,
									msg);
						} else if (shareable == BLOG) {
							blogSharingManager.sendInvitation(groupId, c, msg);
						}
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
		shareActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int res = R.string.forum_share_error;
				if (shareable == BLOG) res = R.string.blogs_sharing_error;
				Toast.makeText(shareActivity, res, LENGTH_SHORT).show();
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
