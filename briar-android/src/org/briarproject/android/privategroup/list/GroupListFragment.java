package org.briarproject.android.privategroup.list;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.privategroup.list.GroupListController.GroupListListener;
import org.briarproject.android.privategroup.list.GroupViewHolder.OnGroupRemoveClickListener;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.db.DbException;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

public class GroupListFragment extends BaseFragment implements
		GroupListListener, OnGroupRemoveClickListener {

	public final static String TAG = GroupListFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	public static GroupListFragment newInstance() {
		return new GroupListFragment();
	}

	@Inject
	GroupListController controller;

	private BriarRecyclerView list;
	private GroupListAdapter adapter;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		View v = inflater.inflate(R.layout.list, container, false);

		adapter = new GroupListAdapter(getContext(), this);
		list = (BriarRecyclerView) v.findViewById(R.id.list);
		list.setEmptyText(R.string.groups_list_empty);
		list.setLayoutManager(new LinearLayoutManager(getContext()));
		list.setAdapter(adapter);

		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		controller.setGroupListListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		controller.onStart();
		list.startPeriodicUpdate();
		loadGroups();
	}

	@Override
	public void onStop() {
		super.onStop();
		controller.onStop();
		list.stopPeriodicUpdate();
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.groups_list_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add_group:
				// TODO
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@UiThread
	@Override
	public void onGroupRemoveClick(GroupItem item) {
		controller.removeGroup(item.getId(),
				new UiResultExceptionHandler<Void, DbException>(listener) {
					@Override
					public void onResultUi(Void result) {
						// handled by GroupRemovedEvent and onGroupRemoved()
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO handle error
						finish();
					}
				});
	}

	@UiThread
	@Override
	public void onGroupMessageAdded(GroupMessageHeader header) {
		adapter.incrementRevision();
		int position = adapter.findItemPosition(header.getGroupId());
		GroupItem item = adapter.getItemAt(position);
		if (item != null) {
			item.addMessageHeader(header);
			adapter.updateItemAt(position, item);
		}
	}

	@UiThread
	@Override
	public void onGroupAdded(GroupId groupId) {
		loadGroups();
	}

	@UiThread
	@Override
	public void onGroupRemoved(GroupId groupId) {
		adapter.incrementRevision();
		adapter.removeItem(groupId);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void loadGroups() {
		final int revision = adapter.getRevision();
		controller.loadGroups(
				new UiResultExceptionHandler<Collection<GroupItem>, DbException>(
						listener) {
					@Override
					public void onResultUi(Collection<GroupItem> groups) {
						if (revision == adapter.getRevision()) {
							adapter.incrementRevision();
							if (groups.isEmpty()) list.showData();
							else adapter.addAll(groups);
						} else {
							LOG.info("Concurrent update, reloading");
							loadGroups();
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO handle this error
						finish();
					}
				});
	}

}
