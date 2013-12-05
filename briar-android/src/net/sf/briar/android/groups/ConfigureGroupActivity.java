package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.util.CommonLayoutParams.WRAP_WRAP;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.contact.SelectContactsDialog;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class ConfigureGroupActivity extends RoboFragmentActivity
implements OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ConfigureGroupActivity.class.getName());

	private boolean subscribed = false;
	private CheckBox subscribeCheckBox = null;
	private RadioGroup radioGroup = null;
	private RadioButton visibleToAll = null, visibleToSome = null;
	private Button doneButton = null;
	private ProgressBar progress = null;
	private NoContactsDialog noContactsDialog = null;
	private SelectContactsDialog selectContactsDialog = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;
	private volatile Group group = null;
	private volatile Collection<ContactId> selected = Collections.emptyList();

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		GroupId id = new GroupId(b);
		String name = i.getStringExtra("net.sf.briar.GROUP_NAME");
		if(name == null) throw new IllegalStateException();
		setTitle(name);
		b = i.getByteArrayExtra("net.sf.briar.GROUP_SALT");
		if(b == null) throw new IllegalStateException();
		group = new Group(id, name, b);
		subscribed = i.getBooleanExtra("net.sf.briar.SUBSCRIBED", false);
		boolean all = i.getBooleanExtra("net.sf.briar.VISIBLE_TO_ALL", false);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

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

		FragmentManager fm = getSupportFragmentManager();
		Fragment f = fm.findFragmentByTag("NoContactsDialog");
		if(f == null) noContactsDialog = new NoContactsDialog();
		else noContactsDialog = (NoContactsDialog) f;
		noContactsDialog.setListener(this);

		f = fm.findFragmentByTag("SelectContactsDialog");
		if(f == null) selectContactsDialog = new SelectContactsDialog();
		else selectContactsDialog = (SelectContactsDialog) f;
		selectContactsDialog.setListener(this);
	}

	public void onClick(View view) {
		if(view == subscribeCheckBox) {
			boolean subscribe = subscribeCheckBox.isChecked();
			visibleToAll.setEnabled(subscribe);
			visibleToSome.setEnabled(subscribe);
		} else if(view == visibleToSome) {
			loadContacts();
		} else if(view == doneButton) {
			boolean subscribe = subscribeCheckBox.isChecked();
			boolean all = visibleToAll.isChecked();
			Collection<ContactId> visible =
					Collections.unmodifiableCollection(selected);
			// Replace the button with a progress bar
			doneButton.setVisibility(GONE);
			progress.setVisibility(VISIBLE);
			// Update the blog in a background thread
			if(subscribe || subscribed)
				updateGroup(subscribe, subscribed, all, visible);
		}
	}

	private void loadContacts() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<Contact> contacts = db.getContacts();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts(contacts);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayContacts(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				FragmentManager fm = getSupportFragmentManager();
				if(contacts.isEmpty()) {
					noContactsDialog.show(fm, "NoContactsDialog");
				} else {
					selectContactsDialog.setContacts(contacts);
					selectContactsDialog.show(fm, "SelectContactsDialog");
				}
			}
		});
	}

	private void updateGroup(final boolean subscribe,
			final boolean wasSubscribed, final boolean all,
			final Collection<ContactId> visible) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					if(subscribe) {
						if(!wasSubscribed) db.subscribe(group);
						db.setVisibleToAll(group.getId(), all);
						if(!all) db.setVisibility(group.getId(), visible);
					} else if(wasSubscribed) {
						db.unsubscribe(group);
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Update took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
				runOnUiThread(new Runnable() {
					public void run() {
						finish();
					}
				});
			}
		});
	}

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {}

	public void contactsSelected(Collection<ContactId> selected) {
		this.selected = selected;
	}

	public void contactSelectionCancelled() {}
}
