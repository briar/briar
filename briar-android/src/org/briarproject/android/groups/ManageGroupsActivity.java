package org.briarproject.android.groups;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.groups.ManageGroupsItem.NONE;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupStatus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class ManageGroupsActivity extends BriarActivity
implements EventListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ManageGroupsActivity.class.getName());

	private ManageGroupsAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		adapter = new ManageGroupsAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_MATCH);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(this);
		setContentView(loading);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadGroups();
	}

	private void loadGroups() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<GroupStatus> available = db.getAvailableGroups();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayGroups(available);
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

	private void displayGroups(final Collection<GroupStatus> available) {
		runOnUiThread(new Runnable() {
			public void run() {
				setContentView(list);
				adapter.clear();
				for(GroupStatus s : available)
					adapter.add(new ManageGroupsItem(s));
				adapter.sort(ManageGroupsItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
	}

	public void eventOccurred(Event e) {
		if(e instanceof RemoteSubscriptionsUpdatedEvent) {
			if(LOG.isLoggable(INFO))
				LOG.info("Remote subscriptions changed, reloading");
			loadGroups();
		} else if(e instanceof SubscriptionAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Group added, reloading");
			loadGroups();
		} else if(e instanceof SubscriptionRemovedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Group removed, reloading");
			loadGroups();
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ManageGroupsItem item = adapter.getItem(position);
		if(item == NONE) return;
		GroupStatus s = item.getGroupStatus();
		Group g = s.getGroup();
		Intent i = new Intent(this, ConfigureGroupActivity.class);
		i.putExtra("briar.GROUP_ID", g.getId().getBytes());
		i.putExtra("briar.GROUP_NAME", g.getName());
		i.putExtra("briar.GROUP_SALT", g.getSalt());
		i.putExtra("briar.SUBSCRIBED", s.isSubscribed());
		i.putExtra("briar.VISIBLE_TO_ALL", s.isVisibleToAll());
		startActivity(i);
	}
}
