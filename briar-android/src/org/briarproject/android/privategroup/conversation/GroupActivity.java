package org.briarproject.android.privategroup.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
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

import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;

public class GroupActivity extends
		ThreadListActivity<PrivateGroup, GroupMessageItem, GroupMessageHeader> {

	@Inject
	GroupController controller;

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

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		loadNamedGroup();

		list.setEmptyText(R.string.groups_no_messages);
	}

	@Override
	protected void onNamedGroupLoaded(PrivateGroup group) {
		setTitle(group.getName());
		// Created by
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(getString(R.string.groups_created_by,
					group.getAuthor().getName()));
		}
	}

	@Override
	@LayoutRes
	protected int getLayout() {
		return R.layout.activity_forum;
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
	protected int getMaxBodyLength() {
		return MAX_GROUP_POST_BODY_LENGTH;
	}

	@Override
	@StringRes
	protected int getItemPostedString() {
		return R.string.groups_message_sent;
	}

	@Override
	@StringRes
	protected int getItemReceivedString() {
		return R.string.groups_message_received;
	}

}
