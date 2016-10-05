package org.briarproject.android.sharing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;

public class ShareBlogMessageFragment extends ShareMessageFragment {

	public final static String TAG = ShareBlogMessageFragment.class.getName();

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile BlogSharingManager blogSharingManager;

	public static ShareBlogMessageFragment newInstance(GroupId groupId,
			Collection<ContactId> contacts) {

		ShareBlogMessageFragment fragment = new ShareBlogMessageFragment();
		fragment.setArguments(getArguments(groupId, contacts));
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setTitle(R.string.blogs_sharing_share);

		View v = super.onCreateView(inflater, container, savedInstanceState);
		ui.message.setButtonText(getString(R.string.blogs_sharing_button));
		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	protected void share(final String msg) {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : getContacts()) {
						blogSharingManager.sendInvitation(getGroupId(), c, msg);
					}
				} catch (DbException e) {
					sharingError();
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	protected void sharingError() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int res = R.string.blogs_sharing_error;
				Toast.makeText(getContext(), res, LENGTH_SHORT).show();
			}
		});
	}
}
