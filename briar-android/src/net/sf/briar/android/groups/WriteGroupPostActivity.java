package net.sf.briar.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.identity.CreateIdentityActivity;
import net.sf.briar.android.identity.LocalAuthorItem;
import net.sf.briar.android.identity.LocalAuthorItemComparator;
import net.sf.briar.android.identity.LocalAuthorSpinnerAdapter;
import net.sf.briar.android.util.HorizontalSpace;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.PrivateKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.content.res.Resources;
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
import android.widget.Toast;

public class WriteGroupPostActivity extends RoboActivity
implements OnItemSelectedListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WriteGroupPostActivity.class.getName());

	private LocalAuthorSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private ImageButton sendButton = null;
	private TextView to = null;
	private EditText content = null;
	private AuthorId localAuthorId = null;
	private GroupId groupId = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;
	@Inject @CryptoExecutor private volatile Executor cryptoExecutor;
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile MessageFactory messageFactory;
	private volatile MessageId parentId = null;
	private volatile long timestamp = -1;
	private volatile LocalAuthor localAuthor = null;
	private volatile Group group = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);

		b = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);
		timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);

		if(state != null) {
			b = state.getByteArray("net.sf.briar.LOCAL_AUTHOR_ID");
			if(b != null) localAuthorId = new AuthorId(b);
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

		adapter = new LocalAuthorSpinnerAdapter(this, true);
		spinner = new Spinner(this);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		header.addView(spinner);

		header.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the group
		sendButton.setOnClickListener(this);
		header.addView(sendButton);
		layout.addView(header);

		to = new TextView(this);
		to.setTextSize(18);
		to.setPadding(10, 0, 10, 10);
		to.setText(R.string.to);
		layout.addView(to);

		content = new EditText(this);
		content.setId(1);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		layout.addView(content);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadAuthorsAndGroup();
	}

	private void loadAuthorsAndGroup() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> localAuthors = db.getLocalAuthors();
					group = db.getGroup(groupId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayAuthorsAndGroup(localAuthors);
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

	private void displayAuthorsAndGroup(
			final Collection<LocalAuthor> localAuthors) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(localAuthors.isEmpty()) throw new IllegalStateException();
				adapter.clear();
				for(LocalAuthor a : localAuthors)
					adapter.add(new LocalAuthorItem(a));
				adapter.sort(LocalAuthorItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					LocalAuthorItem item = adapter.getItem(i);
					if(item == LocalAuthorItem.ANONYMOUS) continue;
					if(item == LocalAuthorItem.NEW) continue;
					if(item.getLocalAuthor().getId().equals(localAuthorId)) {
						localAuthor = item.getLocalAuthor();
						spinner.setSelection(i);
						break;
					}
				}
				Resources res = getResources();
				String format = res.getString(R.string.format_to);
				to.setText(String.format(format, group.getName()));
				sendButton.setEnabled(true);
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
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthorItem item = adapter.getItem(position);
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
	}

	public void onNothingSelected(AdapterView<?> parent) {
		localAuthor = null;
		localAuthorId = null;
	}

	public void onClick(View view) {
		if(group == null) throw new IllegalStateException();
		try {
			createMessage(content.getText().toString().getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		Toast.makeText(this, R.string.post_sent_toast, LENGTH_LONG).show();
		finish();
	}

	private void createMessage(final byte[] body) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				// Don't use an earlier timestamp than the parent
				long time = System.currentTimeMillis();
				time = Math.max(time, timestamp + 1);
				Message m;
				try {
					if(localAuthor == null) {
						m = messageFactory.createAnonymousMessage(parentId,
								group, "text/plain", time, body);
					} else {
						KeyParser keyParser = crypto.getSignatureKeyParser();
						byte[] b = localAuthor.getPrivateKey();
						PrivateKey authorKey = keyParser.parsePrivateKey(b);
						m = messageFactory.createPseudonymousMessage(parentId,
								group, localAuthor, authorKey, "text/plain",
								time, body);
					}
				} catch(GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
				storeMessage(m);
			}
		});
	}

	private void storeMessage(final Message m) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					db.addLocalMessage(m);
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
