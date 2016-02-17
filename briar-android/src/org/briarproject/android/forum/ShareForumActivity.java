package org.briarproject.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.contact.SelectContactsDialog;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

public class ShareForumActivity extends BriarActivity
implements OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ShareForumActivity.class.getName());

	private RadioGroup radioGroup = null;
	private RadioButton shareWithAll = null, shareWithSome = null;
	private Button shareButton = null;
	private ProgressBar progress = null;
	private boolean changed = false;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ContactManager contactManager;
	@Inject private volatile ForumSharingManager forumSharingManager;
	private volatile GroupId groupId = null;
	private volatile Collection<Contact> contacts = null;
	private volatile Collection<ContactId> selected = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra("briar.FORUM_NAME");
		if (forumName == null) throw new IllegalStateException();
		setTitle(forumName);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		radioGroup = new RadioGroup(this);
		radioGroup.setOrientation(VERTICAL);
		radioGroup.setPadding(0, 0, 0, pad);

		shareWithAll = new RadioButton(this);
		shareWithAll.setId(2);
		shareWithAll.setText(R.string.forum_share_with_all);
		shareWithAll.setOnClickListener(this);
		radioGroup.addView(shareWithAll);

		shareWithSome = new RadioButton(this);
		shareWithSome.setId(3);
		shareWithSome.setText(R.string.forum_share_with_some);
		shareWithSome.setOnClickListener(this);
		radioGroup.addView(shareWithSome);

		layout.addView(radioGroup);

		shareButton = new Button(this);
		shareButton.setLayoutParams(WRAP_WRAP);
		shareButton.setText(R.string.share_button);
		shareButton.setOnClickListener(this);
		layout.addView(shareButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	public void onClick(View view) {
		if (view == shareWithAll) {
			changed = true;
		} else if (view == shareWithSome) {
			changed = true;
			if (contacts == null) loadVisibility();
			else displayVisibility();
		} else if (view == shareButton) {
			if (changed) {
				share();
			} else {
				finish();
			}
		}
	}

	private void share() {
		// Replace the button with a progress bar
		shareButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		// Update the group in a background thread
		storeVisibility(shareWithAll.isChecked());
	}

	private void loadVisibility() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					contacts = contactManager.getActiveContacts();
					selected = forumSharingManager.getSharedWith(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayVisibility();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayVisibility() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (contacts.isEmpty()) {
					NoContactsDialog builder = new NoContactsDialog();
					builder.setListener(ShareForumActivity.this);
					builder.build(ShareForumActivity.this).show();
				} else {
					SelectContactsDialog builder = new SelectContactsDialog();
					builder.setListener(ShareForumActivity.this);
					builder.setContacts(contacts);
					builder.setSelected(selected);
					builder.build(ShareForumActivity.this).show();
				}
			}
		});
	}

	private void storeVisibility(final boolean all) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					if (all) forumSharingManager.setSharedWithAll(groupId);
					else forumSharingManager.setSharedWith(groupId,
							selected);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Update took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				finishOnUiThread();
			}
		});
	}

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {
		radioGroup.clearCheck();
	}

	public void contactsSelected(Collection<ContactId> selected) {
		this.selected = Collections.unmodifiableCollection(selected);
		share();
	}

	public void contactSelectionCancelled() {
		radioGroup.clearCheck();
	}
}
