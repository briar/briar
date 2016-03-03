package org.briarproject.android.forum;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.fragment.BaseEventFragment;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
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

public class ForumListFragment extends BaseEventFragment implements
		AdapterView.OnItemClickListener, View.OnClickListener {

	public final static String TAG = "ForumListFragment";

	private static final Logger LOG =
			Logger.getLogger(ForumListFragment.class.getName());

	public static ForumListFragment newInstance() {

		Bundle args = new Bundle();

		ForumListFragment fragment = new ForumListFragment();
		fragment.setArguments(args);
		return fragment;
	}

	private static final int MENU_ITEM_UNSUBSCRIBE = 1;

	private TextView empty = null;
	private ForumListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private TextView available = null;
	private ImageButton newForumButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile ForumManager forumManager;
	@Inject protected volatile ForumSharingManager forumSharingManager;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		LinearLayout layout = new LinearLayout(getContext());
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		int pad = LayoutUtils.getPadding(getContext());

		empty = new TextView(getContext());
		empty.setLayoutParams(MATCH_WRAP_1);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_forums);
		empty.setVisibility(GONE);
		layout.addView(empty);

		adapter = new ForumListAdapter(getContext());
		list = new ListView(getContext());
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setOnCreateContextMenuListener(this);
		list.setVisibility(GONE);
		layout.addView(list);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(getContext());
		layout.addView(loading);

		available = new TextView(getContext());
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

		layout.addView(new HorizontalBorder(getContext()));

		LinearLayout footer = new LinearLayout(getContext());
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		newForumButton = new ImageButton(getContext());
		newForumButton.setBackgroundResource(0);
		newForumButton.setImageResource(R.drawable.social_new_chat);
		newForumButton.setOnClickListener(this);
		footer.addView(newForumButton);
		layout.addView(footer);

		return layout;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadHeaders();
	}

	private void loadHeaders() {
		clearHeaders();
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					boolean displayedHeaders = false;
					for (Forum f : forumManager.getForums()) {
						try {
							Collection<ForumPostHeader> headers =
									forumManager.getPostHeaders(f.getId());
							displayHeaders(f, headers);
							displayedHeaders = true;
						} catch (NoSuchGroupException e) {
							// Continue
						}
					}
					int available =
							forumSharingManager.getAvailableForums().size();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					if (!displayedHeaders) displayEmpty();
					displayAvailable(available);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void clearHeaders() {
		listener.runOnUiThread(new Runnable() {
			public void run() {
				empty.setVisibility(GONE);
				list.setVisibility(GONE);
				available.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
			}
		});
	}

	private void displayHeaders(final Forum f,
			final Collection<ForumPostHeader> headers) {
		listener.runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				// Remove the old item, if any
				ForumListItem item = findForum(f.getId());
				if (item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new ForumListItem(f, headers));
				adapter.sort(ForumListItemComparator.INSTANCE);
				selectFirstUnread();
			}
		});
	}

	private void displayEmpty() {
		listener.runOnUiThread(new Runnable() {
			public void run() {
				empty.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
			}
		});
	}

	private void displayAvailable(final int availableCount) {
		listener.runOnUiThread(new Runnable() {
			public void run() {
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

	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading");
			loadAvailable();
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum added, reloading");
				loadHeaders();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum removed, reloading");
				loadHeaders();
			}
		} else if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			if (m.isValid()) {
				ClientId c = m.getClientId();
				if (c.equals(forumManager.getClientId())) {
					LOG.info("Forum post added, reloading");
					loadHeaders(m.getMessage().getGroupId());
				} else if (!m.isLocal()
						&& c.equals(forumSharingManager.getClientId())) {
					LOG.info("Available forums updated, reloading");
					loadAvailable();
				}
			}
		}
	}

	private void loadHeaders(final GroupId g) {
		listener.runOnDbThread(new Runnable() {
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
				} catch (NoSuchGroupException e) {
					removeForum(g);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void removeForum(final GroupId g) {
		listener.runOnUiThread(new Runnable() {
			public void run() {
				ForumListItem item = findForum(g);
				if (item != null) {
					adapter.remove(item);
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
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					int available =
							forumSharingManager.getAvailableForums().size();
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
			startActivity(new Intent(getContext(),
					AvailableForumsActivity.class));
		} else if (view == newForumButton) {
			startActivity(new Intent(getContext(), CreateForumActivity.class));
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		Intent i = new Intent(getContext(), ForumActivity.class);
		Forum f = adapter.getItem(position).getForum();
		i.putExtra("briar.GROUP_ID", f.getId().getBytes());
		i.putExtra("briar.FORUM_NAME", f.getName());
		startActivity(i);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,
			ContextMenuInfo info) {
		String delete = getString(R.string.unsubscribe);
		menu.add(NONE, MENU_ITEM_UNSUBSCRIBE, NONE, delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuItem) {
		if (menuItem.getItemId() == MENU_ITEM_UNSUBSCRIBE) {
			ContextMenuInfo info = menuItem.getMenuInfo();
			int position = ((AdapterContextMenuInfo) info).position;
			ForumListItem item = adapter.getItem(position);
			unsubscribe(item.getForum());
			String unsubscribed = getString(R.string.unsubscribed_toast);
			Toast.makeText(getContext(), unsubscribed, LENGTH_SHORT).show();
		}
		return true;
	}

	private void unsubscribe(final Forum f) {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumSharingManager.removeForum(f);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing forum took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
