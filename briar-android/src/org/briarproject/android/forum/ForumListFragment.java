package org.briarproject.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseEventFragment;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.design.widget.Snackbar.LENGTH_INDEFINITE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;

public class ForumListFragment extends BaseEventFragment implements
		View.OnClickListener {

	public final static String TAG = "ForumListFragment";

	private static final Logger LOG =
			Logger.getLogger(ForumListFragment.class.getName());


	private BriarRecyclerView list;
	private ForumListAdapter adapter;
	private Snackbar snackbar;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile ForumManager forumManager;
	@Inject protected volatile ForumSharingManager forumSharingManager;

	public static ForumListFragment newInstance() {

		Bundle args = new Bundle();

		ForumListFragment fragment = new ForumListFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		View contentView =
				inflater.inflate(R.layout.fragment_forum_list, container,
						false);

		adapter = new ForumListAdapter(getActivity());

		list = (BriarRecyclerView) contentView.findViewById(R.id.forumList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_forums));
		list.periodicallyUpdateContent();

		snackbar = Snackbar.make(list, "", LENGTH_INDEFINITE);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.setAction(R.string.show_forums, this);
		snackbar.setActionTextColor(ContextCompat
				.getColor(getContext(), R.color.briar_button_positive));

		return contentView;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onResume() {
		super.onResume();

		loadForumHeaders();
		loadAvailableForums();
	}

	@Override
	public void onPause() {
		super.onPause();

		adapter.clear();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.forum_list_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_create_forum:
				Intent intent =
						new Intent(getContext(), CreateForumActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void loadForumHeaders() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					// load forums
					long now = System.currentTimeMillis();
					Collection<ForumListItem> forums = new ArrayList<>();
					for (Forum f : forumManager.getForums()) {
						try {
							Collection<ForumPostHeader> headers =
									forumManager.getPostHeaders(f.getId());
							forums.add(new ForumListItem(f, headers));
						} catch (NoSuchGroupException e) {
							// Continue
						}
					}
					displayForumHeaders(forums);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayForumHeaders(final Collection<ForumListItem> forums) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (forums.size() > 0) adapter.addAll(forums);
				else list.showData();
			}
		});
	}

	private void loadAvailableForums() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					int available =
							forumSharingManager.getInvited().size();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading available took " + duration + " ms");
					displayAvailableForums(available);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayAvailableForums(final int availableCount) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (availableCount == 0) {
					snackbar.dismiss();
				} else {
					snackbar.show();
					snackbar.setText(getResources().getQuantityString(
							R.plurals.forums_shared, availableCount,
							availableCount));
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading available forums");
			loadAvailableForums();
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum added, reloading forums");
				loadForumHeaders();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum removed, removing from list");
				removeForum(g.getGroup().getId());
			}
		} else if (e instanceof MessageStateChangedEvent) {
			MessageStateChangedEvent m = (MessageStateChangedEvent) e;
			ClientId c = m.getClientId();
			if (m.getState() == DELIVERED &&
					c.equals(forumManager.getClientId())) {
				LOG.info("Forum post added, reloading");
				loadForumHeaders(m.getMessage().getGroupId());
			}
		} else if (e instanceof ForumInvitationReceivedEvent) {
			loadAvailableForums();
		}
	}

	private void loadForumHeaders(final GroupId g) {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Forum f = forumManager.getForum(g);
					Collection<ForumPostHeader> headers =
							forumManager.getPostHeaders(g);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					updateForum(new ForumListItem(f, headers));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void updateForum(final ForumListItem item) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.updateItem(item);
			}
		});
	}

	private void removeForum(final GroupId g) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ForumListItem item = adapter.getItem(g);
				if (item != null) adapter.remove(item);
			}
		});
	}

	@Override
	public void onClick(View view) {
		// snackbar click
		startActivity(new Intent(getContext(), ForumInvitationsActivity.class));
	}
}
