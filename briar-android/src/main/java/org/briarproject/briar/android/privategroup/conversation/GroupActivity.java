package org.briarproject.briar.android.privategroup.conversation;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.bramble.api.contact.ContactId;
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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_GROUP_INVITE;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupActivity extends
		ThreadListActivity<PrivateGroup, GroupMessageAdapter, GroupMessageItem, GroupMessageHeader>
		implements GroupListener, OnClickListener {

	@Inject
	GroupController controller;

	private boolean isCreator, isDissolved = false;
	private MenuItem revealMenuItem, inviteMenuItem, leaveMenuItem,
			dissolveMenuItem;

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

		Toolbar toolbar = setUpCustomToolbar(false);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		loadNamedGroup();

		// Open member list on Toolbar click
		if (toolbar != null) {
			toolbar.setOnClickListener(
					new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent i = new Intent(GroupActivity.this,
									GroupMemberListActivity.class);
							i.putExtra(GROUP_ID, groupId.getBytes());
							startActivity(i);
						}
					});
		}

		setGroupEnabled(false);
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
						handleDbException(exception);
					}
				});
	}

	@Override
	protected void onNamedGroupLoaded(final PrivateGroup group) {
		setTitle(group.getName());
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
						handleDbException(exception);
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_actions, menu);

		revealMenuItem = menu.findItem(R.id.action_group_reveal);
		inviteMenuItem = menu.findItem(R.id.action_group_invite);
		leaveMenuItem = menu.findItem(R.id.action_group_leave);
		dissolveMenuItem = menu.findItem(R.id.action_group_dissolve);
		showMenuItems();

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_group_member_list:
				Intent i1 = new Intent(this, GroupMemberListActivity.class);
				i1.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i1);
				return true;
			case R.id.action_group_reveal:
				Intent i2 = new Intent(this, RevealContactsActivity.class);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i2);
				return true;
			case R.id.action_group_invite:
				Intent i3 = new Intent(this, GroupInviteActivity.class);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				startActivityForResult(i3, REQUEST_GROUP_INVITE);
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
		if (request == REQUEST_GROUP_INVITE && result == RESULT_OK) {
			displaySnackbar(R.string.groups_invitation_sent);
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
	public void onReplyClick(GroupMessageItem item) {
		if (!isDissolved) super.onReplyClick(item);
	}

	private void setGroupEnabled(boolean enabled) {
		isDissolved = !enabled;
		textInput.setSendButtonEnabled(enabled);
		list.getRecyclerView().setAlpha(enabled ? 1f : 0.5f);

		if (!enabled) {
			textInput.setVisibility(GONE);
			if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
			if (textInput.isEmojiDrawerOpen()) textInput.hideEmojiDrawer();
		} else {
			textInput.setVisibility(VISIBLE);
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
						handleDbException(exception);
					}
				});
	}

	@Override
	public void onContactRelationshipRevealed(AuthorId memberId, ContactId c,
			Visibility v) {
		adapter.updateVisibility(memberId, v);

		sharingController.add(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
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
