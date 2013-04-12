package net.sf.briar.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.identity.LocalAuthorItem.ANONYMOUS;
import static net.sf.briar.android.identity.LocalAuthorItem.NEW;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.identity.CreateIdentityActivity;
import net.sf.briar.android.identity.LocalAuthorItem;
import net.sf.briar.android.identity.LocalAuthorItemComparator;
import net.sf.briar.android.identity.LocalAuthorSpinnerAdapter;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.LocalGroup;
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

public class WriteBlogPostActivity extends BriarActivity
implements OnItemSelectedListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WriteBlogPostActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private CryptoComponent crypto;
	@Inject private MessageFactory messageFactory;
	private LocalAuthorSpinnerAdapter fromAdapter = null;
	private LocalGroupSpinnerAdapter toAdapter = null;
	private Spinner fromSpinner = null, toSpinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	private volatile LocalAuthor localAuthor = null;
	private volatile LocalGroup localGroup = null;
	private volatile GroupId localGroupId = null;
	private volatile MessageId parentId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b != null) localGroupId = new GroupId(b);
		b = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		TextView from = new TextView(this);
		from.setTextSize(18);
		from.setPadding(10, 10, 10, 10);
		from.setText(R.string.from);
		header.addView(from);

		fromAdapter = new LocalAuthorSpinnerAdapter(this, true);
		fromSpinner = new Spinner(this);
		fromSpinner.setAdapter(fromAdapter);
		fromSpinner.setOnItemSelectedListener(this);
		header.addView(fromSpinner);

		header.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled when a group is selected
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

		toAdapter = new LocalGroupSpinnerAdapter(this);
		toSpinner = new Spinner(this);
		toSpinner.setAdapter(toAdapter);
		toSpinner.setOnItemSelectedListener(this);
		header.addView(toSpinner);
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
		loadLocalAuthors();
		loadLocalGroups();
	}

	private void loadLocalAuthors() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> localAuthors = db.getLocalAuthors();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading authors took " + duration + " ms");
					displayLocalAuthors(localAuthors);
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

	private void displayLocalAuthors(
			final Collection<LocalAuthor> localAuthors) {
		runOnUiThread(new Runnable() {
			public void run() {
				fromAdapter.clear();
				for(LocalAuthor a : localAuthors)
					fromAdapter.add(new LocalAuthorItem(a));
				fromAdapter.sort(LocalAuthorItemComparator.INSTANCE);
				fromAdapter.notifyDataSetChanged();
			}
		});
	}

	private void loadLocalGroups() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					Collection<LocalGroup> groups = db.getLocalGroups();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading groups took " + duration + " ms");
					displayLocalGroups(groups);
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

	private void displayLocalGroups(final Collection<LocalGroup> groups) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(groups.isEmpty()) finish();
				int index = -1;
				for(LocalGroup g : groups) {
					if(g.getId().equals(localGroupId)) {
						localGroup = g;
						index = toAdapter.getCount();
					}
					toAdapter.add(g);
				}
				if(index != -1) toSpinner.setSelection(index);
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
		if(parent == fromSpinner) {
			LocalAuthorItem item = fromAdapter.getItem(position);
			if(item == ANONYMOUS) {
				localAuthor = null;
			} else if(item == NEW) {
				localAuthor = null;
				startActivity(new Intent(this, CreateIdentityActivity.class));
			} else {
				localAuthor = item.getLocalAuthor();
			}
		} else if(parent == toSpinner) {
			localGroup = toAdapter.getItem(position);
			localGroupId = localGroup.getId();
			sendButton.setEnabled(true);
		}
	}

	public void onNothingSelected(AdapterView<?> parent) {
		if(parent == fromSpinner) {
			localAuthor = null;
		} else if(parent == toSpinner) {
			localGroup = null;
			localGroupId = null;
			sendButton.setEnabled(false);
		}
	}

	public void onClick(View view) {
		if(localGroup == null) throw new IllegalStateException();
		try {
			byte[] b = content.getText().toString().getBytes("UTF-8");
			storeMessage(createMessage(b));
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		finish();
	}

	private Message createMessage(byte[] body) throws IOException,
	GeneralSecurityException {
		KeyParser keyParser = crypto.getSignatureKeyParser();
		byte[] groupKeyBytes = localGroup.getPrivateKey();
		PrivateKey groupKey = keyParser.parsePrivateKey(groupKeyBytes);
		if(localAuthor == null) {
			return messageFactory.createAnonymousMessage(parentId, localGroup,
					groupKey, "text/plain", body);
		} else {
			byte[] authorKeyBytes = localAuthor.getPrivateKey();
			PrivateKey authorKey = keyParser.parsePrivateKey(authorKeyBytes);
			return messageFactory.createPseudonymousMessage(parentId,
					localGroup, groupKey, localAuthor, authorKey, "text/plain",
					body);
		}
	}

	private void storeMessage(final Message m) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					db.addLocalGroupMessage(m);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
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
}
