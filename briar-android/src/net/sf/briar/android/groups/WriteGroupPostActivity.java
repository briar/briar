package net.sf.briar.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.identity.CreateIdentityActivity;
import net.sf.briar.android.identity.LocalAuthorItem;
import net.sf.briar.android.identity.LocalAuthorItemComparator;
import net.sf.briar.android.identity.LocalAuthorSpinnerAdapter;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
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

public class WriteGroupPostActivity extends RoboActivity
implements OnItemSelectedListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WriteGroupPostActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private CryptoComponent crypto;
	@Inject private MessageFactory messageFactory;
	private LocalAuthorSpinnerAdapter fromAdapter = null;
	private GroupSpinnerAdapter toAdapter = null;
	private Spinner fromSpinner = null, toSpinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;
	private AuthorId localAuthorId = null;
	private GroupId groupId = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	private volatile LocalAuthor localAuthor = null;
	private volatile Group group = null;
	private volatile MessageId parentId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b != null) groupId = new GroupId(b);
		b = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);

		if(state != null) {
			b = state.getByteArray("net.sf.briar.LOCAL_AUTHOR_ID");
			if(b != null) localAuthorId = new AuthorId(b);
			b = state.getByteArray("net.sf.briar.GROUP_ID");
			if(b != null) groupId = new GroupId(b);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		TextView from = new TextView(this);
		from.setTextSize(18);
		from.setPadding(10, 10, 0, 10);
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

		toAdapter = new GroupSpinnerAdapter(this);
		toSpinner = new Spinner(this);
		toSpinner.setAdapter(toAdapter);
		toSpinner.setOnItemSelectedListener(this);
		header.addView(toSpinner);
		layout.addView(header);

		content = new EditText(this);
		content.setId(1);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
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
		loadGroups();
	}

	private void loadLocalAuthors() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
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
					LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayLocalAuthors(
			final Collection<LocalAuthor> localAuthors) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(localAuthors.isEmpty()) throw new IllegalStateException();
				fromAdapter.clear();
				for(LocalAuthor a : localAuthors)
					fromAdapter.add(new LocalAuthorItem(a));
				fromAdapter.sort(LocalAuthorItemComparator.INSTANCE);
				fromAdapter.notifyDataSetChanged();
				int count = fromAdapter.getCount();
				for(int i = 0; i < count; i++) {
					LocalAuthorItem item = fromAdapter.getItem(i);
					if(item == LocalAuthorItem.ANONYMOUS) continue;
					if(item == LocalAuthorItem.NEW) continue;
					if(item.getLocalAuthor().getId().equals(localAuthorId)) {
						localAuthor = item.getLocalAuthor();
						fromSpinner.setSelection(i);
						break;
					}
				}
			}
		});
	}

	private void loadGroups() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
					List<Group> groups = new ArrayList<Group>();
					long now = System.currentTimeMillis();
					for(Group g : db.getSubscriptions())
						if(!g.isRestricted()) groups.add(g);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading groups took " + duration + " ms");
					displayGroups(Collections.unmodifiableList(groups));
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayGroups(final Collection<Group> groups) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(groups.isEmpty()) finish();
				toAdapter.clear();
				for(Group g : groups) toAdapter.add(new GroupItem(g));
				toAdapter.sort(GroupItemComparator.INSTANCE);
				toAdapter.notifyDataSetChanged();
				int count = toAdapter.getCount();
				for(int i = 0; i < count; i++) {
					GroupItem g = toAdapter.getItem(i);
					if(g == GroupItem.NEW) continue;
					if(g.getGroup().getId().equals(groupId)) {
						group = g.getGroup();
						toSpinner.setSelection(i);
						break;
					}
				}
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if(localAuthorId != null) {
			byte[] b =  localAuthorId.getBytes();
			state.putByteArray("net.sf.briar.LOCAL_AUTHOR_ID", b);
		}
		if(groupId != null) {
			byte[] b =  groupId.getBytes();
			state.putByteArray("net.sf.briar.GROUP_ID", b);
		}
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
			if(item == LocalAuthorItem.ANONYMOUS) {
				localAuthor = null;
				localAuthorId = null;
			} else if(item == LocalAuthorItem.NEW) {
				localAuthor = null;
				localAuthorId = null;
				startActivity(new Intent(this, CreateIdentityActivity.class));
			} else {
				localAuthor = item.getLocalAuthor();
				localAuthorId = localAuthor.getId();
			}
		} else if(parent == toSpinner) {
			GroupItem item = toAdapter.getItem(position);
			if(item == GroupItem.NEW) {
				group = null;
				groupId = null;
				startActivity(new Intent(this, CreateGroupActivity.class));
			} else {
				group = item.getGroup();
				groupId = group.getId();
				sendButton.setEnabled(true);
			}
		}
	}

	public void onNothingSelected(AdapterView<?> parent) {
		if(parent == fromSpinner) {
			localAuthor = null;
			localAuthorId = null;
		} else if(parent == toSpinner) {
			group = null;
			groupId = null;
			sendButton.setEnabled(false);
		}
	}

	public void onClick(View view) {
		if(group == null) throw new IllegalStateException();
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

	// FIXME: This should happen on a CryptoExecutor thread
	private Message createMessage(byte[] body) throws IOException,
	GeneralSecurityException {
		if(localAuthor == null) {
			return messageFactory.createAnonymousMessage(parentId, group,
					"text/plain", body);
		} else {
			KeyParser keyParser = crypto.getSignatureKeyParser();
			byte[] authorKeyBytes = localAuthor.getPrivateKey();
			PrivateKey authorKey = keyParser.parsePrivateKey(authorKeyBytes);
			return messageFactory.createPseudonymousMessage(parentId,
					group, localAuthor, authorKey, "text/plain", body);
		}
	}

	private void storeMessage(final Message m) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
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
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}
}
