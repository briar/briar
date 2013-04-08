package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.AuthorNameComparator;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.LocalAuthorSpinnerAdapter;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
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

public class WriteGroupMessageActivity extends BriarActivity
implements OnItemSelectedListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WriteGroupMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	private LocalAuthorSpinnerAdapter fromAdapter = null;
	private GroupSpinnerAdapter toAdapter = null;
	private Spinner fromSpinner = null, toSpinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile MessageFactory messageFactory;
	private volatile boolean restricted = false;
	private volatile LocalAuthor localAuthor = null;
	private volatile Group group = null;
	private volatile GroupId groupId = null;
	private volatile MessageId parentId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		restricted = i.getBooleanExtra("net.sf.briar.RESTRICTED", false);
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b != null) groupId = new GroupId(b);
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

		fromAdapter = new LocalAuthorSpinnerAdapter(this);
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
		to.setPadding(10, 10, 10, 10);
		to.setText(R.string.to);
		header.addView(to);

		toAdapter = new GroupSpinnerAdapter(this);
		toSpinner = new Spinner(this);
		toSpinner.setAdapter(toAdapter);
		toSpinner.setOnItemSelectedListener(this);
		header.addView(toSpinner);
		layout.addView(header);

		content = new EditText(this);
		content.setPadding(10, 10, 10, 10);
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
		loadGroups();
	}

	private void loadLocalAuthors() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					displayLocalAuthors(db.getLocalAuthors());
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
				for(LocalAuthor a : localAuthors) fromAdapter.add(a);
				fromAdapter.sort(AuthorNameComparator.INSTANCE);
			}
		});
	}

	private void loadGroups() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					List<Group> groups = new ArrayList<Group>();
					if(restricted) {
						groups.addAll(db.getLocalGroups());
					} else {
						for(Group g : db.getSubscriptions())
							if(!g.isRestricted()) groups.add(g);
					}
					groups = Collections.unmodifiableList(groups);
					displayGroups(groups);
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

	private void displayGroups(final Collection<Group> groups) {
		runOnUiThread(new Runnable() {
			public void run() {
				int index = -1;
				for(Group g : groups) {
					if(g.getId().equals(groupId)) {
						group = g;
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
			localAuthor = fromAdapter.getItem(position);
		} else if(parent == toSpinner) {
			group = toAdapter.getItem(position);
			groupId = group.getId();
			sendButton.setEnabled(true);
		}
	}

	public void onNothingSelected(AdapterView<?> parent) {
		if(parent == fromSpinner) {
			localAuthor = null;
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
			storeMessage(localAuthor, group, b);
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		finish();
	}

	private void storeMessage(final LocalAuthor localAuthor, final Group group,
			final byte[] body) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					// FIXME: Anonymous/pseudonymous, restricted/unrestricted
					Message m = messageFactory.createAnonymousMessage(parentId,
							group, "text/plain", body);
					db.addLocalGroupMessage(m);
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
