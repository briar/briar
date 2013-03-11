package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.inject.Inject;

public class WriteMessageActivity extends BriarActivity
implements OnClickListener, OnItemSelectedListener {

	private static final Logger LOG =
			Logger.getLogger(WriteMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;
	@Inject private MessageFactory messageFactory;

	private ContactId contactId = null;
	private MessageId parentId = null;
	private ContactNameSpinnerAdapter adapter = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int cid = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(cid != -1) contactId = new ContactId(cid);
		byte[] pid = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(pid != null) parentId = new MessageId(pid);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout actionBar = new LinearLayout(this);
		actionBar.setLayoutParams(CommonLayoutParams.MATCH_WRAP);
		actionBar.setOrientation(HORIZONTAL);
		actionBar.setGravity(CENTER_VERTICAL);

		TextView to = new TextView(this);
		to.setTextSize(18);
		to.setPadding(10, 10, 10, 10);
		to.setText(R.string.message_to);
		actionBar.addView(to);

		adapter = new ContactNameSpinnerAdapter(this);
		final Spinner spinner = new Spinner(this);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					final Collection<Contact> contacts = db.getContacts();
					runOnUiThread(new Runnable() {
						public void run() {
							for(Contact c : contacts) {
								if(c.getId().equals(contactId))
									spinner.setSelection(adapter.getCount());
								adapter.add(c);
							}
						}
					});
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
		actionBar.addView(spinner);

		actionBar.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false);
		sendButton.setOnClickListener(this);
		actionBar.addView(sendButton);
		layout.addView(actionBar);

		content = new EditText(this);
		content.setPadding(10, 10, 10, 10);
		if(state != null && bundleEncrypter.decrypt(state)) {
			Parcelable p = state.getParcelable("net.sf.briar.CONTENT");
			if(p != null) content.onRestoreInstanceState(p);
		}
		layout.addView(content);

		setContentView(layout);

		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		Parcelable p = content.onSaveInstanceState();
		state.putParcelable("net.sf.briar.CONTENT", p);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(contactId == null) throw new IllegalStateException();
		final Message m;
		try {
			byte[] body = content.getText().toString().getBytes("UTF-8");
			m = messageFactory.createPrivateMessage(parentId, "text/plain",
					body);
		} catch(IOException e) {
			throw new RuntimeException(e);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		final ContactId contactId = this.contactId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					db.addLocalPrivateMessage(m, contactId);
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
		finish();
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		contactId = adapter.getItem(position).getId();
		sendButton.setEnabled(true);
	}

	public void onNothingSelected(AdapterView<?> parent) {
		contactId = null;
		sendButton.setEnabled(false);
	}
}
