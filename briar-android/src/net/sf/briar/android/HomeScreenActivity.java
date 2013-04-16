package net.sf.briar.android;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import static net.sf.briar.api.messaging.Rating.GOOD;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService.BriarBinder;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.blogs.BlogListActivity;
import net.sf.briar.android.contact.ContactListActivity;
import net.sf.briar.android.groups.GroupListActivity;
import net.sf.briar.android.messages.ConversationListActivity;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.android.ReferenceManager;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DbException;
import net.sf.briar.util.StringUtils;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.inject.Inject;

public class HomeScreenActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(HomeScreenActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private ReferenceManager referenceManager = null;
	@Inject private DatabaseConfig databaseConfig = null;
	@Inject @DatabaseUiExecutor private Executor dbUiExecutor = null;
	@Inject @CryptoExecutor private Executor cryptoExecutor = null;
	private boolean bound = false;
	private TextView tryAgain = null;
	private Button continueButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db = null;
	@Inject private volatile CryptoComponent crypto = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		Intent i = getIntent();
		boolean quit = i.getBooleanExtra("net.sf.briar.QUIT", false);
		long handle = i.getLongExtra("net.sf.briar.LOCAL_AUTHOR_HANDLE", -1);
		if(quit) {
			// The activity was launched from the notification bar
			showSpinner();
			bindService();
			quit();
		} else if(handle != -1) {
			// The activity was launched from the setup wizard
			showSpinner();
			startService(new Intent(BriarService.class.getName()));
			bindService();
			storeLocalAuthor(referenceManager.removeReference(handle,
					LocalAuthor.class));
		} else if(databaseConfig.getEncryptionKey() == null) {
			// The activity was launched from the splash screen
			showPasswordPrompt();
		} else {
			// The activity has been launched before
			showButtons();
			bindService();
		}
	}

	private void showSpinner() {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);
		ProgressBar progress = new ProgressBar(this);
		progress.setIndeterminate(true);
		layout.addView(progress);
		setContentView(layout);
	}

	private void bindService() {
		bound = bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void quit() {
		new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					BriarService service = ((BriarBinder) binder).getService();
					service.waitForStartup();
					// Shut down the service and wait for it to shut down
					if(LOG.isLoggable(INFO)) LOG.info("Shutting down service");
					service.shutdown();
					service.waitForShutdown();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
				}
				// Finish the activity and kill the JVM
				runOnUiThread(new Runnable() {
					public void run() {
						finish();
						if(LOG.isLoggable(INFO)) LOG.info("Exiting");
						System.exit(0);
					}
				});
			}
		}.start();
	}

	private void storeLocalAuthor(final LocalAuthor a) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					db.addLocalAuthor(a);
					db.setRating(a.getId(), GOOD);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
					runOnUiThread(new Runnable() {
						public void run() {
							showButtons();
						}
					});
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

	private void showPasswordPrompt() {
		SharedPreferences prefs = getSharedPreferences("db", MODE_PRIVATE);
		String hex = prefs.getString("key", null);
		if(hex == null) throw new IllegalStateException();
		final byte[] encrypted = StringUtils.fromHexString(hex);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView enterPassword = new TextView(this);
		enterPassword.setGravity(CENTER);
		enterPassword.setTextSize(18);
		enterPassword.setPadding(10, 10, 10, 10);
		enterPassword.setText(R.string.enter_password);
		layout.addView(enterPassword);

		final EditText passwordEntry = new EditText(this);
		passwordEntry.setMaxLines(1);
		passwordEntry.setPadding(10, 0, 10, 10);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
		passwordEntry.setInputType(inputType);
		passwordEntry.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int action, KeyEvent e) {
				validatePassword(encrypted, passwordEntry.getText());
				return true;
			}
		});
		layout.addView(passwordEntry);

		tryAgain = new TextView(this);
		tryAgain.setGravity(CENTER);
		tryAgain.setTextSize(14);
		tryAgain.setPadding(10, 10, 10, 10);
		tryAgain.setText(R.string.try_again);
		tryAgain.setVisibility(GONE);
		layout.addView(tryAgain);

		continueButton = new Button(this);
		continueButton.setLayoutParams(WRAP_WRAP);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				validatePassword(encrypted, passwordEntry.getText());
			}
		});
		layout.addView(continueButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);
		setContentView(layout);
	}

	private void validatePassword(final byte[] encrypted, Editable e) {
		if(tryAgain == null || continueButton == null || progress == null)
			return;
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		// Replace the button with a progress bar
		continueButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		// Decrypt the database key in a background thread
		int length = e.length();
		final char[] password = new char[length];
		e.getChars(0, length, password, 0);
		e.delete(0, length);
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if(key == null) {
					tryAgain();
				} else {
					databaseConfig.setEncryptionKey(key);
					showButtonsAndStartService();
				}
			}
		});
	}

	private void tryAgain() {
		runOnUiThread(new Runnable() {
			public void run() {
				tryAgain.setVisibility(VISIBLE);
				continueButton.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
			}
		});
	}

	private void showButtonsAndStartService() {
		runOnUiThread(new Runnable() {
			public void run() {
				showButtons();
				startService(new Intent(BriarService.class.getName()));
				bindService();
			}
		});
	}

	private void showButtons() {
		ListView.LayoutParams matchMatch =
				new ListView.LayoutParams(MATCH_PARENT, MATCH_PARENT);
		final List<Button> buttons = new ArrayList<Button>();

		Button contactsButton = new Button(this);
		contactsButton.setLayoutParams(matchMatch);
		contactsButton.setBackgroundResource(0);
		contactsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_person, 0, 0);
		contactsButton.setText(R.string.contact_list_button);
		contactsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(HomeScreenActivity.this,
						ContactListActivity.class));
			}
		});
		buttons.add(contactsButton);

		Button messagesButton = new Button(this);
		messagesButton.setLayoutParams(matchMatch);
		messagesButton.setBackgroundResource(0);
		messagesButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.content_email, 0, 0);
		messagesButton.setText(R.string.messages_button);
		messagesButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(HomeScreenActivity.this,
						ConversationListActivity.class));
			}
		});
		buttons.add(messagesButton);

		Button groupsButton = new Button(this);
		groupsButton.setLayoutParams(matchMatch);
		groupsButton.setBackgroundResource(0);
		groupsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_chat, 0, 0);
		groupsButton.setText(R.string.groups_button);
		groupsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(HomeScreenActivity.this,
						GroupListActivity.class));
			}
		});
		buttons.add(groupsButton);

		Button blogsButton = new Button(this);
		blogsButton.setLayoutParams(matchMatch);
		blogsButton.setBackgroundResource(0);
		blogsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_blog, 0, 0);
		blogsButton.setText(R.string.blogs_button);
		blogsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(HomeScreenActivity.this,
						BlogListActivity.class));
			}
		});
		buttons.add(blogsButton);

		Button syncButton = new Button(this);
		syncButton.setLayoutParams(matchMatch);
		syncButton.setBackgroundResource(0);
		syncButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.navigation_refresh, 0, 0);
		syncButton.setText(R.string.synchronize_button);
		syncButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				// FIXME: Hook this button up to an activity
			}
		});
		buttons.add(syncButton);

		Button quitButton = new Button(this);
		quitButton.setLayoutParams(matchMatch);
		quitButton.setBackgroundResource(0);
		quitButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.device_access_accounts, 0, 0);
		quitButton.setText(R.string.quit_button);
		quitButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				showSpinner();
				quit();
			}
		});
		buttons.add(quitButton);

		GridView grid = new GridView(this);
		grid.setLayoutParams(matchMatch);
		grid.setGravity(CENTER);
		grid.setPadding(5, 5, 5, 5);
		grid.setBackgroundColor(getResources().getColor(
				R.color.home_screen_background));
		grid.setNumColumns(2);
		grid.setAdapter(new BaseAdapter() {

			public int getCount() {
				return buttons.size();
			}

			public Object getItem(int position) {
				return buttons.get(position);
			}

			public long getItemId(int position) {
				return 0;
			}

			public View getView(int position, View convertView,
					ViewGroup parent) {
				return buttons.get(position);
			}
		});
		setContentView(grid);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(bound) unbindService(serviceConnection);
	}
}
