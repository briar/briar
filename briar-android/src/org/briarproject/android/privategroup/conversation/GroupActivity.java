package org.briarproject.android.privategroup.conversation;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.threaded.ThreadListActivity;
import org.briarproject.android.threaded.ThreadListController;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;

import javax.inject.Inject;

public class GroupActivity extends
		ThreadListActivity<PrivateGroup, GroupMessageItem, GroupMessageHeader, GroupMessageAdapter> {

	@Inject
	protected GroupController controller;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected ThreadListController<PrivateGroup, GroupMessageItem, GroupMessageHeader> getController() {
		return controller;
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		list.setEmptyText(R.string.groups_no_messages);
	}

	@Override
	protected @LayoutRes int getLayout() {
		return R.layout.activity_forum;
	}

	@Override
	protected void setActionBarTitle(@Nullable String title) {
		if (title != null) setTitle(title);
		loadGroupItem();
	}

	@Override
	protected void onGroupItemLoaded(PrivateGroup group) {
		super.onGroupItemLoaded(group);
		// Created by
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(getString(R.string.groups_created_by,
					group.getAuthor().getName()));
		}
	}

	@Override
	protected GroupMessageAdapter createAdapter(
			LinearLayoutManager layoutManager) {
		return new GroupMessageAdapter(this, layoutManager);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_group_compose_message:
				showTextInput(null);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected int getItemPostedString() {
		return R.string.groups_message_sent;
	}

	@Override
	protected int getItemReceivedString() {
		return R.string.groups_message_received;
	}

}
