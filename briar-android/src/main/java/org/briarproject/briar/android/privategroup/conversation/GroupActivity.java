package org.briarproject.briar.android.privategroup.conversation;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiExceptionHandler;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.privategroup.conversation.GroupController.GroupListener;
import org.briarproject.briar.android.privategroup.creation.GroupInviteActivity;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.briar.android.threaded.ThreadListActivity;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.Visibility;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.view.View.GONE;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupActivity extends
		ThreadListActivity<PrivateGroup, GroupMessageAdapter, GroupMessageItem, GroupMessageHeader>
		implements GroupListener, OnClickListener {

	private final static int REQUEST_INVITE = 2;

	@Inject
	GroupController controller;

	private boolean isCreator, isDissolved = false;
	private MenuItem writeMenuItem, revealMenuItem, inviteMenuItem,
			leaveMenuItem, dissolveMenuItem;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected ThreadListController<PrivateGroup, GroupMessageItem, GroupMessageHeader> getController() {
		return controller;
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		loadNamedGroup();

		setGroupEnabled(false);
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
	protected void loadItems() {
		controller.isDissolved(
				new UiResultExceptionHandler<Boolean, DbException>(this) {
					@Override
					public void onResultUi(Boolean isDissolved) {
						setGroupEnabled(!isDissolved);
						GroupActivity.super.loadItems();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

	@Override
	protected void onNamedGroupLoaded(final PrivateGroup group) {
		setTitle(group.getName());
		// Created by
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(getString(R.string.groups_created_by,
					group.getCreator().getName()));
		}
		controller.loadLocalAuthor(
				new UiResultExceptionHandler<LocalAuthor, DbException>(this) {
					@Override
					public void onResultUi(LocalAuthor author) {
						isCreator = group.getCreator().equals(author);
						adapter.setPerspective(isCreator);
						showMenuItems();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_actions, menu);

		writeMenuItem = menu.findItem(R.id.action_group_compose_message);
		revealMenuItem = menu.findItem(R.id.action_group_reveal);
		inviteMenuItem = menu.findItem(R.id.action_group_invite);
		leaveMenuItem = menu.findItem(R.id.action_group_leave);
		dissolveMenuItem = menu.findItem(R.id.action_group_dissolve);
		showMenuItems();

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		ActivityOptionsCompat options =
				makeCustomAnimation(this, android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		switch (item.getItemId()) {
			case R.id.action_group_compose_message:
				showTextInput(null);
				return true;
			case R.id.action_group_member_list:
				Intent i1 = new Intent(this, GroupMemberListActivity.class);
				i1.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivity(this, i1, options.toBundle());
				return true;
			case R.id.action_group_reveal:
				Intent i2 = new Intent(this, RevealContactsActivity.class);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivity(this, i2, options.toBundle());
				return true;
			case R.id.action_group_invite:
				Intent i3 = new Intent(this, GroupInviteActivity.class);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivityForResult(this, i3, REQUEST_INVITE,
						options.toBundle());
				return true;
			case R.id.action_group_leave:
				showLeaveGroupDialog();
				return true;
			case R.id.action_group_dissolve:
				showDissolveGroupDialog();
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		if (request == REQUEST_INVITE && result == RESULT_OK) {
			displaySnackbarShort(R.string.groups_invitation_sent);
		} else super.onActivityResult(request, result, data);
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

	@Override
	public void onReplyClick(GroupMessageItem item) {
		if (!isDissolved) super.onReplyClick(item);
	}

	private void setGroupEnabled(boolean enabled) {
		isDissolved = !enabled;
		if (writeMenuItem != null) writeMenuItem.setVisible(enabled);
		textInput.setSendButtonEnabled(enabled);
		list.getRecyclerView().setAlpha(enabled ? 1f : 0.5f);

		if (!enabled) {
			textInput.setVisibility(GONE);
			if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
			if (textInput.isEmojiDrawerOpen()) textInput.hideEmojiDrawer();
		}
	}

	private void showMenuItems() {
		if (leaveMenuItem == null || dissolveMenuItem == null) return;
		if (isCreator) {
			revealMenuItem.setVisible(false);
			inviteMenuItem.setVisible(true);
			leaveMenuItem.setVisible(false);
			dissolveMenuItem.setVisible(true);
		} else {
			revealMenuItem.setVisible(true);
			inviteMenuItem.setVisible(false);
			leaveMenuItem.setVisible(true);
			dissolveMenuItem.setVisible(false);
		}
		writeMenuItem.setVisible(!isDissolved);
	}

	private void showLeaveGroupDialog() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_leave_dialog_title));
		builder.setMessage(getString(R.string.groups_leave_dialog_message));
		builder.setNegativeButton(R.string.dialog_button_leave, this);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void showDissolveGroupDialog() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolve_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolve_dialog_message));
		builder.setNegativeButton(R.string.groups_dissolve_button, this);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		controller.deleteNamedGroup(
				new UiExceptionHandler<DbException>(this) {
					// The activity is going to be destroyed by the
					// GroupRemovedEvent being fired
					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

	@Override
	public void onContactRelationshipRevealed(AuthorId memberId, Visibility v) {
		adapter.updateVisibility(memberId, v);
	}

	@Override
	public void onGroupDissolved() {
		setGroupEnabled(false);
		AlertDialog.Builder builder =
			new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolved_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolved_dialog_message));
		builder.setNeutralButton(R.string.ok, null);
		builder.show();
	}

}
