package org.briarproject.android.privategroup.memberlist;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.inject.Inject;

public class GroupMemberListActivity extends BriarActivity {

	@Inject
	GroupMemberListController controller;

	private MemberListAdapter adapter;
	private BriarRecyclerView list;
	private GroupId groupId;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.list);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);

		list = (BriarRecyclerView) findViewById(R.id.list);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(linearLayoutManager);
		adapter = new MemberListAdapter(this);
		list.setAdapter(adapter);
	}

	@Override
	public void onStart() {
		super.onStart();
		controller.loadMembers(groupId,
				new UiResultExceptionHandler<Collection<MemberListItem>, DbException>(this) {
					@Override
					public void onResultUi(Collection<MemberListItem> members) {
						adapter.addAll(members);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		list.stopPeriodicUpdate();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
