package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP_1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarFragmentActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class GroupListActivity extends BriarFragmentActivity
implements OnClickListener, DatabaseListener, NoGroupsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(GroupListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private GroupListAdapter adapter = null;
	private ListView list = null;
	private ImageButton newGroupButton = null, composeButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	private volatile boolean restricted = false;
	private volatile boolean noGroups = true;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		Intent i = getIntent();
		restricted = i.getBooleanExtra("net.sf.briar.RESTRICTED", false);
		String title = i.getStringExtra("net.sf.briar.TITLE");
		if(title == null) throw new IllegalStateException();
		setTitle(title);

		adapter = new GroupListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(adapter);
		layout.addView(list);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		footer.addView(new HorizontalSpace(this));

		newGroupButton = new ImageButton(this);
		newGroupButton.setBackgroundResource(0);
		if(restricted)
			newGroupButton.setImageResource(R.drawable.social_new_blog);
		else newGroupButton.setImageResource(R.drawable.social_new_chat);
		newGroupButton.setOnClickListener(this);
		footer.addView(newGroupButton);
		footer.addView(new HorizontalSpace(this));

		composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		footer.addView(composeButton);
		footer.addView(new HorizontalSpace(this));
		layout.addView(footer);

		setContentView(layout);

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					if(restricted) noGroups = db.getLocalGroups().isEmpty();
					for(Group g : db.getSubscriptions()) {
						// Filter out restricted/unrestricted groups
						if(g.isRestricted() != restricted) continue;
						if(!restricted) noGroups = false;
						try {
							Collection<GroupMessageHeader> headers =
									db.getMessageHeaders(g.getId());
							displayHeaders(g, headers);
						} catch(NoSuchSubscriptionException e) {
							if(LOG.isLoggable(INFO))
								LOG.info("Subscription removed");
						}
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
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

	private void displayHeaders(final Group g,
			final Collection<GroupMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				// Remove the old item, if any
				GroupListItem item = findGroup(g.getId());
				if(item != null) adapter.remove(item);
				// Add a new item if there are any headers to display
				if(!headers.isEmpty()) {
					List<GroupMessageHeader> headerList =
							new ArrayList<GroupMessageHeader>(headers);
					adapter.add(new GroupListItem(g, headerList));
					adapter.sort(GroupComparator.INSTANCE);
				}
				selectFirstUnread();
			} 
		});
	}

	private GroupListItem findGroup(GroupId g) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			GroupListItem item = adapter.getItem(i);
			if(item.getGroupId().equals(g)) return item;
		}
		return null; // Not found
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			if(adapter.getItem(i).getUnreadCount() > 0) {
				firstUnread = i;
				break;
			}
		}
		if(firstUnread == -1) list.setSelection(count - 1);
		else list.setSelection(firstUnread);
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == newGroupButton) {
			// FIXME: Hook this button up to an activity
		} else if(view == composeButton) {
			if(noGroups) {
				NoGroupsDialog dialog = new NoGroupsDialog();
				dialog.setListener(this);
				dialog.setRestricted(restricted);
				dialog.show(getSupportFragmentManager(), "NoGroupsDialog");
			} else {
				Intent i = new Intent(this, WriteGroupMessageActivity.class);
				i.putExtra("net.sf.briar.RESTRICTED", restricted);
				startActivity(i);
			}
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof GroupMessageAddedEvent) {
			Group g = ((GroupMessageAddedEvent) e).getGroup();
			if(g.isRestricted() == restricted) {
				if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
				loadHeaders(g);
			}
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders();
		} else if(e instanceof SubscriptionRemovedEvent) {
			Group g = ((SubscriptionRemovedEvent) e).getGroup();
			if(g.isRestricted() == restricted) {
				// Reload the group, expecting NoSuchSubscriptionException
				if(LOG.isLoggable(INFO)) LOG.info("Group removed, reloading");
				loadHeaders(g);
			}
		}
	}

	private void loadHeaders(final Group g) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					Collection<GroupMessageHeader> headers =
							db.getMessageHeaders(g.getId());
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					displayHeaders(g, headers);
				} catch(NoSuchSubscriptionException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Subscription removed");
					removeGroup(g.getId());
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

	private void removeGroup(final GroupId g) {
		runOnUiThread(new Runnable() {
			public void run() {
				GroupListItem item = findGroup(g);
				if(item != null) {
					adapter.remove(item);
					selectFirstUnread();
				}
			}
		});
	}

	public void createGroupButtonClicked() {
		// FIXME: Hook this button up to an activity
	}

	public void cancelButtonClicked() {
		// That's nice dear
	}

	private static class GroupComparator implements Comparator<GroupListItem> {

		private static final GroupComparator INSTANCE = new GroupComparator();

		public int compare(GroupListItem a, GroupListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.getGroupName(),
					b.getGroupName());
		}
	}
}