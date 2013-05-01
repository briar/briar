package net.sf.briar.android.groups;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.groups.GroupListItem.MANAGE;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP_1;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.android.widgets.ListLoadingProgressBar;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.SubscriptionAddedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.GroupStatus;
import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.google.inject.Inject;

public class GroupListActivity extends RoboFragmentActivity
implements DatabaseListener, OnClickListener, NoGroupsDialog.Listener,
OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(GroupListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private GroupListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton newGroupButton = null, composeButton = null;
	private ImageButton manageGroupsButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new GroupListAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		layout.addView(list);

		// Show a progress bar while the list is loading
		list.setVisibility(GONE);
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		footer.addView(new HorizontalSpace(this));

		newGroupButton = new ImageButton(this);
		newGroupButton.setBackgroundResource(0);
		newGroupButton.setImageResource(R.drawable.social_new_chat);
		newGroupButton.setOnClickListener(this);
		footer.addView(newGroupButton);
		footer.addView(new HorizontalSpace(this));

		composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setOnClickListener(this);
		footer.addView(composeButton);
		footer.addView(new HorizontalSpace(this));

		manageGroupsButton = new ImageButton(this);
		manageGroupsButton.setBackgroundResource(0);
		manageGroupsButton.setImageResource(R.drawable.action_settings);
		manageGroupsButton.setOnClickListener(this);
		footer.addView(manageGroupsButton);
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
		clearHeaders();
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
					int available = 0;
					long now = System.currentTimeMillis();
					for(GroupStatus s : db.getAvailableGroups()) {
						Group g = s.getGroup();
						if(g.isRestricted()) continue;
						if(s.isSubscribed()) {
							try {
								Collection<GroupMessageHeader> headers =
										db.getGroupMessageHeaders(g.getId());
								displayHeaders(g, headers);
							} catch(NoSuchSubscriptionException e) {
								if(LOG.isLoggable(INFO))
									LOG.info("Subscription removed");
							}
						} else {
							available++;
						}
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					displayAvailable(available);
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

	private void clearHeaders() {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void displayHeaders(final Group g,
			final Collection<GroupMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				// Remove the old item, if any
				GroupListItem item = findGroup(g.getId());
				if(item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new GroupListItem(g, headers));
				adapter.sort(ItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				selectFirstUnread();
			} 
		});
	}

	private void displayAvailable(final int available) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				adapter.setAvailable(available);
				adapter.notifyDataSetChanged();
			}
		});
	}

	private GroupListItem findGroup(GroupId g) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			GroupListItem item = adapter.getItem(i);
			if(item == MANAGE) continue;
			if(item.getGroup().getId().equals(g)) return item;
		}
		return null; // Not found
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			GroupListItem item = adapter.getItem(i);
			if(item == MANAGE) continue;
			if(item.getUnreadCount() > 0) {
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

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof GroupMessageAddedEvent) {
			Group g = ((GroupMessageAddedEvent) e).getGroup();
			if(!g.isRestricted()) {
				if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
				loadHeaders(g);
			}
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders();
		} else if(e instanceof RemoteSubscriptionsUpdatedEvent) {
			if(LOG.isLoggable(INFO))
				LOG.info("Remote subscriptions changed, reloading");
			loadAvailable();
		} else if(e instanceof SubscriptionAddedEvent) {
			Group g = ((SubscriptionAddedEvent) e).getGroup();
			if(!g.isRestricted()) {
				if(LOG.isLoggable(INFO)) LOG.info("Group added, reloading");
				loadHeaders();
			}
		} else if(e instanceof SubscriptionRemovedEvent) {
			Group g = ((SubscriptionRemovedEvent) e).getGroup();
			if(!g.isRestricted()) {
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
					serviceConnection.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<GroupMessageHeader> headers =
							db.getGroupMessageHeaders(g.getId());
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
						LOG.info("Interrupted while waiting for database");
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
					adapter.notifyDataSetChanged();
					selectFirstUnread();
				}
			}
		});
	}

	private void loadAvailable() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForDatabase();
					int available = 0;
					long now = System.currentTimeMillis();
					for(GroupStatus s : db.getAvailableGroups()) {
						if(!s.getGroup().isRestricted() && !s.isSubscribed())
							available++;
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading available took " + duration + " ms");
					displayAvailable(available);
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

	public void onClick(View view) {
		if(view == newGroupButton) {
			startActivity(new Intent(this, CreateGroupActivity.class));
		} else if(view == composeButton) {
			if(adapter.isEmpty()) {
				NoGroupsDialog dialog = new NoGroupsDialog();
				dialog.setListener(this);
				dialog.show(getSupportFragmentManager(), "NoGroupsDialog");
			} else {
				startActivity(new Intent(this, WriteGroupPostActivity.class));
			}
		} else if(view == manageGroupsButton) {
			startActivity(new Intent(this, ManageGroupsActivity.class));
		}
	}

	public void groupCreationSelected() {
		startActivity(new Intent(this, CreateGroupActivity.class));
	}

	public void groupCreationCancelled() {}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		GroupListItem item = adapter.getItem(position);
		if(item == MANAGE) {
			startActivity(new Intent(this, ManageGroupsActivity.class));
		} else {
			Intent i = new Intent(this, GroupActivity.class);
			i.putExtra("net.sf.briar.GROUP_ID",
					item.getGroup().getId().getBytes());
			i.putExtra("net.sf.briar.GROUP_NAME", item.getGroup().getName());
			startActivity(i);
		}
	}

	private static class ItemComparator implements Comparator<GroupListItem> {

		private static final ItemComparator INSTANCE = new ItemComparator();

		public int compare(GroupListItem a, GroupListItem b) {
			if(a == b) return 0;
			// The manage groups item comes last
			if(a == MANAGE) return 1;
			if(b == MANAGE) return -1;
			// The item with the newest message comes first
			long aTime = a.getTimestamp(), bTime = b.getTimestamp();
			if(aTime > bTime) return -1;
			if(aTime < bTime) return 1;
			// Break ties by group name
			String aName = a.getGroup().getName();
			String bName = b.getGroup().getName();
			return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
		}
	}
}