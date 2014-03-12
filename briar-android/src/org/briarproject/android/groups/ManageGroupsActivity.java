package org.briarproject.android.groups;

import static android.view.Gravity.CENTER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupStatus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class ManageGroupsActivity extends BriarActivity
implements EventListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ManageGroupsActivity.class.getName());

	private TextView empty = null;
	private ManageGroupsAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		empty = new TextView(this);
		empty.setLayoutParams(MATCH_MATCH);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_forums_available);

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
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<GroupStatus> available = db.getAvailableGroups();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayGroups(available);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayGroups(final Collection<GroupStatus> available) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(available.isEmpty()) {
					setContentView(empty);
				} else {
					setContentView(list);
					adapter.clear();
					for(GroupStatus s : available)
						adapter.add(new ManageGroupsItem(s));
					adapter.sort(ManageGroupsItemComparator.INSTANCE);
					adapter.notifyDataSetChanged();
				}
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
			LOG.info("Remote subscriptions changed, reloading");
			loadGroups();
		} else if(e instanceof SubscriptionAddedEvent) {
			LOG.info("Group added, reloading");
			loadGroups();
		} else if(e instanceof SubscriptionRemovedEvent) {
			LOG.info("Group removed, reloading");
			loadGroups();
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ManageGroupsItem item = adapter.getItem(position);
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
