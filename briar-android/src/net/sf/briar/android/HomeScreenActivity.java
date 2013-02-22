package net.sf.briar.android;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;

import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService.BriarBinder;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.contact.ContactListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout.LayoutParams;

public class HomeScreenActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(HomeScreenActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	Button contactListButton = null, quitButton = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER);

		// If this activity was launched from the notification bar, quit
		if(getIntent().getBooleanExtra("net.sf.briar.QUIT", false)) {
			ProgressBar spinner = new ProgressBar(this);
			spinner.setIndeterminate(true);
			layout.addView(spinner);
			quit();
		} else {
			LinearLayout innerLayout = new LinearLayout(this);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			contactListButton = new Button(this);
			LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			contactListButton.setLayoutParams(lp);
			contactListButton.setText(R.string.contact_list_button);
			contactListButton.setCompoundDrawablesWithIntrinsicBounds(
					R.drawable.social_person, 0, 0, 0);
			contactListButton.setOnClickListener(this);
			innerLayout.addView(contactListButton);

			quitButton = new Button(this);
			quitButton.setLayoutParams(lp);
			quitButton.setText(R.string.quit_button);
			quitButton.setCompoundDrawablesWithIntrinsicBounds(
					R.drawable.navigation_cancel, 0, 0, 0);
			quitButton.setOnClickListener(this);
			innerLayout.addView(quitButton);
			layout.addView(innerLayout);
		}

		setContentView(layout);

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

	public void onClick(View view) {
		if(view == contactListButton)
			startActivity(new Intent(this, ContactListActivity.class));
		else if(view == quitButton) quit();
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
