package net.sf.briar.android.blogs;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.android.BriarFragmentActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.ManageGroupsAdapter;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.SubscriptionAddedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupStatus;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.google.inject.Inject;

public class ManageBlogsActivity extends BriarFragmentActivity
implements DatabaseListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ManageBlogsActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	private ManageGroupsAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		adapter = new ManageGroupsAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_MATCH);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		setContentView(list);

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadAvailableGroups();
	}

	private void loadAvailableGroups() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					List<GroupStatus> available = new ArrayList<GroupStatus>();
					for(GroupStatus s : db.getAvailableGroups())
						if(s.getGroup().isRestricted()) available.add(s);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					available = Collections.unmodifiableList(available);
					displayAvailableGroups(available);
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

	private void displayAvailableGroups(
			final Collection<GroupStatus> available) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(GroupStatus g : available) adapter.add(g);
				adapter.sort(ItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
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
		if(e instanceof RemoteSubscriptionsUpdatedEvent) {
			if(LOG.isLoggable(INFO))
				LOG.info("Remote subscriptions changed, reloading");
			loadAvailableGroups();
		} else if(e instanceof SubscriptionAddedEvent) {
			Group g = ((SubscriptionAddedEvent) e).getGroup();
			if(g.isRestricted()) {
				if(LOG.isLoggable(INFO)) LOG.info("Group added, reloading");
				loadAvailableGroups();
			}
		} else if(e instanceof SubscriptionRemovedEvent) {
			Group g = ((SubscriptionRemovedEvent) e).getGroup();
			if(g.isRestricted()) {
				if(LOG.isLoggable(INFO)) LOG.info("Group removed, reloading");
				loadAvailableGroups();
			}
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		GroupStatus item = adapter.getItem(position);
		Intent i = new Intent(this, ConfigureBlogActivity.class);
		i.putExtra("net.sf.briar.GROUP_ID", item.getGroup().getId().getBytes());
		i.putExtra("net.sf.briar.GROUP_NAME", item.getGroup().getName());
		i.putExtra("net.sf.briar.SUBSCRIBED", item.isSubscribed());
		i.putExtra("net.sf.briar.VISIBLE_TO_ALL", item.isVisibleToAll());
		startActivity(i);
	}

	private static class ItemComparator implements Comparator<GroupStatus> {

		private static final ItemComparator INSTANCE = new ItemComparator();

		public int compare(GroupStatus a, GroupStatus b) {
			String aName = a.getGroup().getName();
			String bName = b.getGroup().getName();
			return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
		}
	}
}
