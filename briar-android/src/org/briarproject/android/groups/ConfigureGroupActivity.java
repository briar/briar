package org.briarproject.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.contact.SelectContactsDialog;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class ConfigureGroupActivity extends BriarActivity
implements OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ConfigureGroupActivity.class.getName());

	private String groupName = null;
	private CheckBox subscribeCheckBox = null;
	private RadioGroup radioGroup = null;
	private RadioButton visibleToAll = null, visibleToSome = null;
	private Button doneButton = null;
	private ProgressBar progress = null;
	private boolean changed = false;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	private volatile GroupId groupId = null;
	private volatile Group group = null;
	private volatile boolean subscribed = false;
	private volatile Collection<Contact> contacts = null;
	private volatile Collection<ContactId> selected = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		groupName = i.getStringExtra("briar.GROUP_NAME");
		if(groupName == null) throw new IllegalStateException();
		setTitle(groupName);
		b = i.getByteArrayExtra("briar.GROUP_SALT");
		if(b == null) throw new IllegalStateException();
		group = new Group(groupId, groupName, b);
		subscribed = i.getBooleanExtra("briar.SUBSCRIBED", false);
		boolean all = i.getBooleanExtra("briar.VISIBLE_TO_ALL", false);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		subscribeCheckBox = new CheckBox(this);
		subscribeCheckBox.setId(1);
		subscribeCheckBox.setText(R.string.subscribe_to_this_forum);
		subscribeCheckBox.setChecked(subscribed);
		subscribeCheckBox.setOnClickListener(this);
		layout.addView(subscribeCheckBox);

		radioGroup = new RadioGroup(this);
		radioGroup.setOrientation(VERTICAL);

		visibleToAll = new RadioButton(this);
		visibleToAll.setId(2);
		visibleToAll.setText(R.string.forum_visible_to_all);
		visibleToAll.setEnabled(subscribed);
		visibleToAll.setOnClickListener(this);
		radioGroup.addView(visibleToAll);

		visibleToSome = new RadioButton(this);
		visibleToSome.setId(3);
		visibleToSome.setText(R.string.forum_visible_to_some);
		visibleToSome.setEnabled(subscribed);
		visibleToSome.setOnClickListener(this);
		radioGroup.addView(visibleToSome);

		if(!subscribed || all) radioGroup.check(visibleToAll.getId());
		else radioGroup.check(visibleToSome.getId());
		layout.addView(radioGroup);

		doneButton = new Button(this);
		doneButton.setLayoutParams(WRAP_WRAP);
		doneButton.setText(R.string.done_button);
		doneButton.setOnClickListener(this);
		layout.addView(doneButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	public void onClick(View view) {
		if(view == subscribeCheckBox) {
			changed = true;
			boolean subscribe = subscribeCheckBox.isChecked();
			visibleToAll.setEnabled(subscribe);
			visibleToSome.setEnabled(subscribe);
		} else if(view == visibleToAll) {
			changed = true;
		} else if(view == visibleToSome) {
			changed = true;
			if(contacts == null) loadContacts();
			else displayContacts();
		} else if(view == doneButton) {
			if(changed) {
				boolean subscribe = subscribeCheckBox.isChecked();
				boolean all = visibleToAll.isChecked();
				// Replace the button with a progress bar
				doneButton.setVisibility(GONE);
				progress.setVisibility(VISIBLE);
				// Update the blog in a background thread
				if(subscribe || subscribed) updateGroup(subscribe, all);
			} else {
				finish();
			}
		}
	}

	private void loadContacts() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					contacts = db.getContacts();
					selected = db.getVisibility(groupId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts() {
		runOnUiThread(new Runnable() {
			public void run() {
				if(contacts.isEmpty()) {
					NoContactsDialog builder = new NoContactsDialog();
					builder.setListener(ConfigureGroupActivity.this);
					builder.build(ConfigureGroupActivity.this).show();
				} else {
					SelectContactsDialog builder = new SelectContactsDialog();
					builder.setListener(ConfigureGroupActivity.this);
					builder.setContacts(contacts);
					builder.setSelected(selected);
					builder.build(ConfigureGroupActivity.this).show();
				}
			}
		});
	}

	private void updateGroup(final boolean subscribe, final boolean all) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					if(subscribe) {
						if(!subscribed) db.addGroup(group);
						db.setVisibleToAll(groupId, all);
						if(!all) db.setVisibility(groupId, selected);
					} else if(subscribed) {
						db.removeGroup(group);
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Update took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				if(subscribe) showGroup();
				else finishOnUiThread();
			}
		});
	}

	private void showGroup() {
		runOnUiThread(new Runnable() {
			public void run() {
				Intent i = new Intent(ConfigureGroupActivity.this,
						GroupActivity.class);
				i.putExtra("briar.GROUP_ID", groupId.getBytes());
				i.putExtra("briar.GROUP_NAME", groupName);
				startActivity(i);
				finish();
			}
		});
	}

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {}

	public void contactsSelected(Collection<ContactId> selected) {
		this.selected = Collections.unmodifiableCollection(selected);
	}

	public void contactSelectionCancelled() {}
}
