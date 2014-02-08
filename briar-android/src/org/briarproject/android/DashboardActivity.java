package org.briarproject.android;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.groups.GroupListActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class DashboardActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(DashboardActivity.class.getName());

	@Inject private ReferenceManager referenceManager;
	@Inject @DatabaseUiExecutor private Executor dbUiExecutor;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		handleIntent(getIntent());
	}

	@Override
	public void onNewIntent(Intent i) {
		super.onNewIntent(i);
		handleIntent(i);
	}

	private void handleIntent(Intent i) {
		boolean failed = i.getBooleanExtra("briar.STARTUP_FAILED", false);
		long handle = i.getLongExtra("briar.LOCAL_AUTHOR_HANDLE", -1);
		if(failed) {
			finish();
			if(LOG.isLoggable(INFO)) LOG.info("Exiting");
			System.exit(0);
		} else if(handle == -1) {
			// The activity has been launched before
			showButtons();
		} else {
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
		}
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
				startActivity(new Intent(DashboardActivity.this,
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
				startActivity(new Intent(DashboardActivity.this,
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
				Toast.makeText(DashboardActivity.this,
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

	private void showSpinner() {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(this);
		progress.setIndeterminate(true);
		layout.addView(progress);

		setContentView(layout);
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
}
