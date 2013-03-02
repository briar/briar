package net.sf.briar.android;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static java.util.logging.Level.INFO;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService.BriarBinder;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.contact.ContactListActivity;
import net.sf.briar.android.messages.ConversationListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

public class HomeScreenActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(HomeScreenActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();
	private final List<Button> buttons = new ArrayList<Button>();

	public HomeScreenActivity() {
		super();
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(LOG.isLoggable(INFO)) LOG.info("Created");

		// If this activity was launched from the notification bar, quit
		if(getIntent().getBooleanExtra("net.sf.briar.QUIT", false)) {
			quit();
		} else {
			ListView.LayoutParams matchParent = new ListView.LayoutParams(
					MATCH_PARENT, MATCH_PARENT);

			Button contactsButton = new Button(this);
			contactsButton.setLayoutParams(matchParent);
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
			messagesButton.setLayoutParams(matchParent);
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

			Button boardsButton = new Button(this);
			boardsButton.setLayoutParams(matchParent);
			boardsButton.setCompoundDrawablesWithIntrinsicBounds(0,
					R.drawable.social_chat, 0, 0);
			boardsButton.setText(R.string.boards_button);
			boardsButton.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					// FIXME: Hook this button up to an activity
				}
			});
			buttons.add(boardsButton);

			Button blogsButton = new Button(this);
			blogsButton.setLayoutParams(matchParent);
			blogsButton.setCompoundDrawablesWithIntrinsicBounds(0,
					R.drawable.social_share, 0, 0);
			blogsButton.setText(R.string.blogs_button);
			blogsButton.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					// FIXME: Hook this button up to an activity
				}
			});
			buttons.add(blogsButton);

			Button syncButton = new Button(this);
			syncButton.setLayoutParams(matchParent);
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
			quitButton.setLayoutParams(matchParent);
			quitButton.setCompoundDrawablesWithIntrinsicBounds(0,
					R.drawable.device_access_accounts, 0, 0);
			quitButton.setText(R.string.quit_button);
			quitButton.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					quit();
				}
			});
			buttons.add(quitButton);

			GridView grid = new GridView(this);
			grid.setLayoutParams(matchParent);
			grid.setGravity(CENTER);
			grid.setPadding(5, 5, 5, 5);
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

		// Start the service and bind to it
		startService(new Intent(BriarService.class.getName()));
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	private void quit() {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT,
				MATCH_PARENT));
		layout.setGravity(CENTER);
		ProgressBar spinner = new ProgressBar(this);
		spinner.setIndeterminate(true);
		layout.addView(spinner);
		setContentView(layout);
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
					// Finish the activity and kill the JVM
					runOnUiThread(new Runnable() {
						public void run() {
							finish();
							if(LOG.isLoggable(INFO)) LOG.info("Exiting");
							System.exit(0);
						}
					});
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
				}
			}
		}.start();
	}
}
