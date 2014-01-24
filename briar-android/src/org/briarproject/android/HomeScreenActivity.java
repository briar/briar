package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarService.BriarBinder;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.groups.GroupListActivity;
import org.briarproject.android.util.FixedVerticalSpace;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.util.StringUtils;

import roboguice.activity.RoboActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
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
import android.widget.Toast;

public class HomeScreenActivity extends RoboActivity {

	// This build expires on 31 January 2014
	private static final long EXPIRY_DATE = 1391126400 * 1000L;

	private static final Logger LOG =
			Logger.getLogger(HomeScreenActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private ReferenceManager referenceManager;
	@Inject private DatabaseConfig databaseConfig;
	@Inject @DatabaseUiExecutor private Executor dbUiExecutor;
	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private boolean bound = false;
	private TextView enterPassword = null;
	private Button continueButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		Intent i = getIntent();
		boolean failed = i.getBooleanExtra("briar.STARTUP_FAILED", false);
		long handle = i.getLongExtra("briar.LOCAL_AUTHOR_HANDLE", -1);
		if(failed) {
			// LifecycleManager failed to start all necessary services
			showStartupFailureNotification();
			finish();
			if(LOG.isLoggable(INFO)) LOG.info("Exiting");
			System.exit(0);
		} else if(System.currentTimeMillis() >= EXPIRY_DATE) {
			showExpiryWarning();
		} else if(handle != -1) {
			// The activity was launched from the setup wizard
			LocalAuthor a = referenceManager.removeReference(handle,
					LocalAuthor.class);
			// The reference may be null if the activity has been recreated,
			// for example due to screen rotation
			if(a == null) {
				showButtons();
			} else {
				showSpinner();
				storeLocalAuthor(a);
			}
			startService(new Intent(BriarService.class.getName()));
			bindService();
		} else if(databaseConfig.getEncryptionKey() == null) {
			// The activity was launched from the splash screen
			showPasswordPrompt();
		} else {
			// The activity has been launched before
			showButtons();
			bindService();
		}
	}

	private void showStartupFailureNotification() {
		NotificationCompat.Builder b = new NotificationCompat.Builder(this);
		b.setSmallIcon(android.R.drawable.stat_notify_error);
		b.setContentTitle(getText(R.string.startup_failed_notification_title));
		b.setContentText(getText(R.string.startup_failed_notification_text));
		// Touch the notification to relaunch the app
		Intent i = new Intent(this, HomeScreenActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		b.setContentIntent(PendingIntent.getActivity(this, 0, i, 0));
		Notification n = b.build();
		Object o = getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.notify(0, n);
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
					// Wait for the service to finish starting up
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
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					db.addLocalAuthor(a);
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
						LOG.info("Interrupted while waiting for database");
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

		int pad = LayoutUtils.getPadding(this);

		enterPassword = new TextView(this);
		enterPassword.setGravity(CENTER);
		enterPassword.setTextSize(18);
		enterPassword.setPadding(pad, pad, pad, 0);
		enterPassword.setText(R.string.enter_password);
		layout.addView(enterPassword);

		final EditText passwordEntry = new EditText(this);
		passwordEntry.setId(1);
		passwordEntry.setMaxLines(1);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
		passwordEntry.setInputType(inputType);
		passwordEntry.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int action, KeyEvent e) {
				validatePassword(encrypted, passwordEntry.getText());
				return true;
			}
		});
		layout.addView(passwordEntry);

		// Adjusting the padding of buttons and EditTexts has the wrong results
		layout.addView(new FixedVerticalSpace(this));

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
		if(enterPassword == null || continueButton == null || progress == null)
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
				enterPassword.setText(R.string.try_again);
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

	private void showExpiryWarning() {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);

		int pad = LayoutUtils.getPadding(this);

		TextView warning = new TextView(this);
		warning.setGravity(CENTER);
		warning.setTextSize(18);
		warning.setPadding(pad, pad, pad, pad);
		warning.setText(R.string.expiry_warning);
		layout.addView(warning);

		setContentView(layout);
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

		Button forumsButton = new Button(this);
		forumsButton.setLayoutParams(matchMatch);
		forumsButton.setBackgroundResource(0);
		forumsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_chat, 0, 0);
		forumsButton.setText(R.string.forums_button);
		forumsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(HomeScreenActivity.this,
						GroupListActivity.class));
			}
		});
		buttons.add(forumsButton);

		Button syncButton = new Button(this);
		syncButton.setLayoutParams(matchMatch);
		syncButton.setBackgroundResource(0);
		syncButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.navigation_refresh, 0, 0);
		syncButton.setText(R.string.synchronize_button);
		syncButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				// FIXME: Hook this button up to an activity
				Toast.makeText(HomeScreenActivity.this,
						R.string.not_implemented_toast, LENGTH_SHORT).show();
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

		int pad = LayoutUtils.getPadding(this);

		GridView grid = new GridView(this);
		grid.setLayoutParams(matchMatch);
		grid.setGravity(CENTER);
		grid.setPadding(pad, pad, pad, pad);
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
