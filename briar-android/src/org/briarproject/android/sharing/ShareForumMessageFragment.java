package org.briarproject.android.sharing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;

public class ShareForumMessageFragment extends ShareMessageFragment {

	public final static String TAG = ShareForumMessageFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	public static ShareForumMessageFragment newInstance(GroupId groupId,
			Collection<ContactId> contacts) {

		ShareForumMessageFragment fragment = new ShareForumMessageFragment();
		fragment.setArguments(getArguments(groupId, contacts));
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setTitle(R.string.forum_share_button);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	protected void share(final String msg) {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : getContacts()) {
						forumSharingManager.
								sendInvitation(getGroupId(), c, msg);
					}
				} catch (DbException e) {
					sharingError();
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	protected void sharingError() {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				int res = R.string.forum_share_error;
				Toast.makeText(getContext(), res, LENGTH_SHORT).show();
			}
		});
	}
}
