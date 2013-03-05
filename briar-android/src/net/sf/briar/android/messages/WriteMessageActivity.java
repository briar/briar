package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;

import com.google.inject.Inject;

public class WriteMessageActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WriteMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;
	@Inject private MessageFactory messageFactory;

	private ContactId contactId = null;
	private String contactName = null;
	private MessageId parentId = null;
	private ImageButton cancelButton = null, sendButton = null;
	private EditText content = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int cid = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(cid == -1) throw new IllegalStateException();
		contactId = new ContactId(cid);
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		byte[] pid = i.getByteArrayExtra("net.sf.briar.MESSAGE_ID");
		if(pid != null) parentId = new MessageId(pid);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		cancelButton = new ImageButton(this);
		cancelButton.setPadding(5, 5, 5, 5);
		cancelButton.setBackgroundResource(0);
		cancelButton.setImageResource(R.drawable.navigation_cancel);
		cancelButton.setOnClickListener(this);
		header.addView(cancelButton);

		sendButton = new ImageButton(this);
		sendButton.setPadding(5, 5, 5, 5);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setOnClickListener(this);
		header.addView(sendButton);
		layout.addView(header);

		ScrollView scrollView = new ScrollView(this);
		content = new EditText(this);
		content.setPadding(10, 10, 10, 10);
		if(state != null && bundleEncrypter.decrypt(state)) {
			Parcelable p = state.getParcelable("net.sf.briar.CONTENT");
			if(p != null) content.onRestoreInstanceState(p);
		}
		scrollView.addView(content);
		layout.addView(scrollView);

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
		if(view == cancelButton) {
			finish();
		} else if(view == sendButton) {
			final Message m;
			try {
				byte[] body = content.getText().toString().getBytes("UTF-8");
				m = messageFactory.createPrivateMessage(parentId,
						"text/plain", body);
			} catch(UnsupportedEncodingException e) {
				throw new RuntimeException(e);
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
	}
}
