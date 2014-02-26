package org.briarproject.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.text.TextUtils.TruncateAt.END;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.CENTER_VERTICAL;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.RelativeLayout.RIGHT_OF;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.identity.LocalAuthorItem;
import org.briarproject.android.identity.LocalAuthorItemComparator;
import org.briarproject.android.identity.LocalAuthorSpinnerAdapter;
import org.briarproject.android.util.CommonLayoutParams;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.AuthorId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.util.StringUtils;

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
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class WriteGroupPostActivity extends BriarActivity
implements OnItemSelectedListener, OnClickListener {

	private static final int REQUEST_CREATE_IDENTITY = 2;
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
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		b = i.getByteArrayExtra("briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);
		timestamp = i.getLongExtra("briar.TIMESTAMP", -1);

		if(state != null) {
			b = state.getByteArray("briar.LOCAL_AUTHOR_ID");
			if(b != null) localAuthorId = new AuthorId(b);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		RelativeLayout header = new RelativeLayout(this);

		TextView from = new TextView(this);
		from.setId(1);
		from.setTextSize(18);
		from.setPadding(0, 0, 0, pad);
		from.setText(R.string.from);
		RelativeLayout.LayoutParams left = CommonLayoutParams.relative();
		left.addRule(ALIGN_PARENT_LEFT);
		left.addRule(CENTER_VERTICAL);
		header.addView(from, left);

		adapter = new LocalAuthorSpinnerAdapter(this, true);
		spinner = new Spinner(this);
		spinner.setId(2);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		RelativeLayout.LayoutParams between = CommonLayoutParams.relative();
		between.addRule(CENTER_VERTICAL);
		between.addRule(RIGHT_OF, 1);
		between.addRule(LEFT_OF, 3);
		header.addView(spinner, between);

		sendButton = new ImageButton(this);
		sendButton.setId(3);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the group
		sendButton.setOnClickListener(this);
		RelativeLayout.LayoutParams right = CommonLayoutParams.relative();
		right.addRule(ALIGN_PARENT_RIGHT);
		right.addRule(CENTER_VERTICAL);
		header.addView(sendButton, right);
		layout.addView(header);

		to = new TextView(this);
		to.setTextSize(18);
		to.setSingleLine();
		to.setEllipsize(END);
		to.setPadding(0, 0, pad, pad);
		to.setText(R.string.to);
		layout.addView(to);

		content = new EditText(this);
		content.setId(4);
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
			state.putByteArray("briar.LOCAL_AUTHOR_ID", b);
		}
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if(request == REQUEST_CREATE_IDENTITY && result == RESULT_OK) {
			byte[] b = data.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
			if(b == null) throw new IllegalStateException();
			localAuthorId = new AuthorId(b);
			loadAuthorsAndGroup();
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
			Intent i = new Intent(this, CreateIdentityActivity.class);
			startActivityForResult(i, REQUEST_CREATE_IDENTITY);
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
		String message = content.getText().toString();
		if(message.equals("")) return;
		createMessage(StringUtils.toUtf8(message));
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
