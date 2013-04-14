package net.sf.briar.android.messages;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.contact.ContactItem;
import net.sf.briar.android.contact.ContactItemComparator;
import net.sf.briar.android.contact.ContactSpinnerAdapter;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
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

public class WritePrivateMessageActivity extends BriarActivity
implements OnItemSelectedListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WritePrivateMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	private TextView from = null;
	private ContactSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile MessageFactory messageFactory;
	private volatile LocalAuthor localAuthor = null;
	private volatile ContactId contactId = null;
	private volatile MessageId parentId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int id = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(id != -1) contactId = new ContactId(id);
		byte[] b = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		from = new TextView(this);
		from.setTextSize(18);
		from.setMaxLines(1);
		from.setPadding(10, 10, 10, 10);
		from.setText(R.string.from);
		header.addView(from);

		header.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the local author
		sendButton.setOnClickListener(this);
		header.addView(sendButton);
		layout.addView(header);

		header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		TextView to = new TextView(this);
		to.setTextSize(18);
		to.setPadding(10, 0, 0, 10);
		to.setText(R.string.to);
		header.addView(to);

		adapter = new ContactSpinnerAdapter(this);
		spinner = new Spinner(this);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		header.addView(spinner);
		layout.addView(header);

		content = new EditText(this);
		content.setPadding(10, 10, 10, 10);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		if(state != null && bundleEncrypter.decrypt(state)) {
			Parcelable p = state.getParcelable("net.sf.briar.CONTENT");
			if(p != null) content.onRestoreInstanceState(p);
		}
		layout.addView(content);

		setContentView(layout);

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadContacts();
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
						LOG.info("Loading contacts took " + duration + " ms");
					displayContacts(contacts);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayContacts(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(contacts.isEmpty()) finish();
				adapter.clear();
				for(Contact c : contacts) adapter.add(new ContactItem(c));
				adapter.sort(ContactItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					ContactItem item = adapter.getItem(i);
					if(item == ContactItem.NEW) continue;
					if(item.getContact().getId().equals(contactId)) {
						spinner.setSelection(i);
						break;
					}
				}
			}
		});
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

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		ContactItem item = adapter.getItem(position);
		if(item == ContactItem.NEW) {
			startActivity(new Intent(this, AddContactActivity.class));
		} else {
			Contact c = item.getContact();
			loadLocalAuthor(c.getLocalAuthorId());
			contactId = c.getId();
		}
	}

	private void loadLocalAuthor(final AuthorId a) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					localAuthor = db.getLocalAuthor(a);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading author took " + duration + " ms");
					displayLocalAuthor();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayLocalAuthor() {
		runOnUiThread(new Runnable() {
			public void run() {
				String format = getResources().getString(R.string.format_from);
				from.setText(String.format(format, localAuthor.getName()));
				sendButton.setEnabled(true);
			}
		});
	}

	public void onNothingSelected(AdapterView<?> parent) {
		contactId = null;
		sendButton.setEnabled(false);
	}

	public void onClick(View view) {
		if(localAuthor == null || contactId == null)
			throw new IllegalStateException();
		try {
			byte[] b = content.getText().toString().getBytes("UTF-8");
			storeMessage(localAuthor, contactId, b);
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		finish();
	}

	private void storeMessage(final LocalAuthor localAuthor,
			final ContactId contactId, final byte[] body) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					Message m = messageFactory.createPrivateMessage(parentId,
							"text/plain", body);
					long now = System.currentTimeMillis();
					db.addLocalPrivateMessage(m, contactId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
}
