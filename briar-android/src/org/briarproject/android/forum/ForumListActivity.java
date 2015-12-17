package org.briarproject.android.forum;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Menu.NONE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class ForumListActivity extends BriarActivity
implements EventListener, OnClickListener, OnItemClickListener,
OnCreateContextMenuListener {

	private static final int MENU_ITEM_UNSUBSCRIBE = 1;
	private static final Logger LOG =
			Logger.getLogger(ForumListActivity.class.getName());

	private final Map<GroupId, GroupId> groupIds =
			new ConcurrentHashMap<GroupId, GroupId>();

	private TextView empty = null;
	private ForumListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private TextView available = null;
	private ImageButton newForumButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ForumManager forumManager;
	@Inject private volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		int pad = LayoutUtils.getPadding(this);

		empty = new TextView(this);
		empty.setLayoutParams(MATCH_WRAP_1);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_forums);
		empty.setVisibility(GONE);
		layout.addView(empty);

		adapter = new ForumListAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setOnCreateContextMenuListener(this);
		list.setVisibility(GONE);
		layout.addView(list);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		available = new TextView(this);
		available.setLayoutParams(MATCH_WRAP);
		available.setGravity(CENTER);
		available.setTextSize(18);
		available.setPadding(pad, pad, pad, pad);
		Resources res = getResources();
		int background = res.getColor(R.color.forums_available_background);
		available.setBackgroundColor(background);
		available.setOnClickListener(this);
		available.setVisibility(GONE);
		layout.addView(available);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		newForumButton = new ImageButton(this);
		newForumButton.setBackgroundResource(0);
		newForumButton.setImageResource(R.drawable.social_new_chat);
		newForumButton.setOnClickListener(this);
		footer.addView(newForumButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		clearHeaders();
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (Forum f : forumManager.getForums()) {
						try {
							Collection<ForumPostHeader> headers =
									forumManager.getPostHeaders(f.getId());
							displayHeaders(f, headers);
						} catch (NoSuchSubscriptionException e) {
							// Continue
						}
					}
					int available = forumManager.getAvailableForums().size();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					displayAvailable(available);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void clearHeaders() {
		runOnUiThread(new Runnable() {
			public void run() {
				groupIds.clear();
				empty.setVisibility(GONE);
				list.setVisibility(GONE);
				available.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void displayHeaders(final Forum f,
			final Collection<ForumPostHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				GroupId id = f.getId();
				groupIds.put(id, id);
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				// Remove the old item, if any
				ForumListItem item = findForum(id);
				if (item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new ForumListItem(f, headers));
				adapter.sort(ForumListItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				selectFirstUnread();
			}
		});
	}

	private void displayAvailable(final int availableCount) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (adapter.isEmpty()) empty.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				if (availableCount == 0) {
					available.setVisibility(GONE);
				} else {
					available.setVisibility(VISIBLE);
					available.setText(getResources().getQuantityString(
							R.plurals.forums_shared, availableCount,
							availableCount));
				}
			}
		});
	}

	private ForumListItem findForum(GroupId g) {
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			ForumListItem item = adapter.getItem(i);
			if (item.getForum().getId().equals(g)) return item;
		}
		return null; // Not found
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			if (adapter.getItem(i).getUnreadCount() > 0) {
				firstUnread = i;
				break;
			}
		}
		if (firstUnread == -1) list.setSelection(count - 1);
		else list.setSelection(firstUnread);
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	public void eventOccurred(Event e) {
		if (e instanceof MessageAddedEvent) {
			GroupId g = ((MessageAddedEvent) e).getGroupId();
			if (groupIds.containsKey(g)) {
				LOG.info("Message added, reloading");
				loadHeaders(g);
			}
		} else if (e instanceof RemoteSubscriptionsUpdatedEvent) {
			LOG.info("Remote subscriptions changed, reloading");
			loadAvailable();
		} else if (e instanceof SubscriptionAddedEvent) {
			LOG.info("Group added, reloading");
			loadHeaders();
		} else if (e instanceof SubscriptionRemovedEvent) {
			Group g = ((SubscriptionRemovedEvent) e).getGroup();
			if (groupIds.containsKey(g.getId())) {
				LOG.info("Group removed, reloading");
				loadHeaders();
			}
		}
	}

	private void loadHeaders(final GroupId g) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Forum f = forumManager.getForum(g);
					Collection<ForumPostHeader> headers =
							forumManager.getPostHeaders(g);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					displayHeaders(f, headers);
				} catch (NoSuchSubscriptionException e) {
					removeForum(g);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void removeForum(final GroupId g) {
		runOnUiThread(new Runnable() {
			public void run() {
				ForumListItem item = findForum(g);
				if (item != null) {
					groupIds.remove(g);
					adapter.remove(item);
					adapter.notifyDataSetChanged();
					if (adapter.isEmpty()) {
						empty.setVisibility(VISIBLE);
						list.setVisibility(GONE);
					} else {
						selectFirstUnread();
					}
				}
			}
		});
	}

	private void loadAvailable() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					int available = forumManager.getAvailableForums().size();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading available took " + duration + " ms");
					displayAvailable(available);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public void onClick(View view) {
		if (view == available) {
			startActivity(new Intent(this, AvailableForumsActivity.class));
		} else if (view == newForumButton) {
			startActivity(new Intent(this, CreateForumActivity.class));
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		Intent i = new Intent(this, ForumActivity.class);
		Forum f = adapter.getItem(position).getForum();
		i.putExtra("briar.GROUP_ID", f.getId().getBytes());
		i.putExtra("briar.FORUM_NAME", f.getName());
		startActivity(i);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,
			ContextMenu.ContextMenuInfo info) {
		String delete = getString(R.string.unsubscribe);
		menu.add(NONE, MENU_ITEM_UNSUBSCRIBE, NONE, delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuItem) {
		if (menuItem.getItemId() == MENU_ITEM_UNSUBSCRIBE) {
			ContextMenuInfo info = menuItem.getMenuInfo();
			int position = ((AdapterContextMenuInfo) info).position;
			ForumListItem item = adapter.getItem(position);
			removeSubscription(item.getForum());
			String unsubscribed = getString(R.string.unsubscribed_toast);
			Toast.makeText(this, unsubscribed, LENGTH_SHORT).show();
		}
		return true;
	}

	private void removeSubscription(final Forum f) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.removeForum(f);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing group took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}