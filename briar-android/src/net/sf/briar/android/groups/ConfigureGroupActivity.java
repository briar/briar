package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarFragmentActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.contact.SelectContactsDialog;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.messages.NoContactsDialog;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.GroupId;
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

import com.google.inject.Inject;

public class ConfigureGroupActivity extends BriarFragmentActivity
implements OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(ConfigureGroupActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private boolean wasSubscribed = false;
	private CheckBox subscribeCheckBox = null;
	private RadioGroup radioGroup = null;
	private RadioButton visibleToAll = null, visibleToSome = null;
	private Button doneButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	private volatile GroupId groupId = null;
	private volatile Collection<ContactId> selected = Collections.emptyList();

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String groupName = i.getStringExtra("net.sf.briar.GROUP_NAME");
		if(groupName == null) throw new IllegalArgumentException();
		setTitle(groupName);
		wasSubscribed = i.getBooleanExtra("net.sf.briar.SUBSCRIBED", false);
		boolean all = i.getBooleanExtra("net.sf.briar.VISIBLE_TO_ALL", false);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		subscribeCheckBox = new CheckBox(this);
		subscribeCheckBox.setText(R.string.subscribe_to_this_group);
		subscribeCheckBox.setChecked(wasSubscribed);
		subscribeCheckBox.setOnClickListener(this);
		layout.addView(subscribeCheckBox);

		radioGroup = new RadioGroup(this);
		radioGroup.setOrientation(VERTICAL);
		radioGroup.setEnabled(wasSubscribed);

		visibleToAll = new RadioButton(this);
		visibleToAll.setId(1);
		visibleToAll.setText(R.string.group_visible_to_all);
		visibleToAll.setOnClickListener(this);
		radioGroup.addView(visibleToAll);

		visibleToSome = new RadioButton(this);
		visibleToSome.setId(2);
		visibleToSome.setText(R.string.group_visible_to_some);
		visibleToSome.setOnClickListener(this);
		radioGroup.addView(visibleToSome);

		if(all) radioGroup.check(1);
		else radioGroup.check(2);
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

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == subscribeCheckBox) {
			radioGroup.setEnabled(subscribeCheckBox.isChecked());
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
			if(subscribe || wasSubscribed)
				updateGroup(subscribe, wasSubscribed, all, visible);
		}
	}

	private void loadContacts() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
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
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayContacts(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(contacts.isEmpty()) {
					NoContactsDialog dialog = new NoContactsDialog();
					dialog.setListener(ConfigureGroupActivity.this);
					dialog.show(getSupportFragmentManager(),
							"NoContactsDialog");
				} else {
					SelectContactsDialog dialog = new SelectContactsDialog();
					dialog.setListener(ConfigureGroupActivity.this);
					dialog.setContacts(contacts);
					dialog.show(getSupportFragmentManager(),
							"SelectContactsDialog");
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
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					if(subscribe) {
						if(!wasSubscribed) db.subscribe(db.getGroup(groupId));
						db.setVisibleToAll(groupId, all);
						if(!all) db.setVisibility(groupId, visible);
					} else if(wasSubscribed) {
						db.unsubscribe(db.getGroup(groupId));
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Update took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
				finishOnUiThread();
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
