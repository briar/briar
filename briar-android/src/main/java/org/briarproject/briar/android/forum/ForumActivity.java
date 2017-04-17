package org.briarproject.briar.android.forum;

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
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.forum.ForumController.ForumListener;
import org.briarproject.briar.android.sharing.ForumSharingStatusActivity;
import org.briarproject.briar.android.sharing.ShareForumActivity;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.briarproject.briar.android.threaded.ThreadListActivity;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumPostHeader;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_SHARE_FORUM;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ForumActivity extends
		ThreadListActivity<Forum, ThreadItemAdapter<ForumItem>, ForumItem, ForumPostHeader>
		implements ForumListener {

	@Inject
	ForumController forumController;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected ThreadListController<Forum, ForumItem, ForumPostHeader> getController() {
		return forumController;
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		Toolbar toolbar = setUpCustomToolbar(false);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		else loadNamedGroup();

		// Open member list on Toolbar click
		if (toolbar != null) {
			toolbar.setOnClickListener(
					new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent i = new Intent(ForumActivity.this,
									ForumSharingStatusActivity.class);
							i.putExtra(GROUP_ID, groupId.getBytes());
							startActivity(i);
						}
					});
		}
	}

	@Override
	protected void onNamedGroupLoaded(Forum forum) {
		setTitle(forum.getName());
	}

	@Override
	protected ThreadItemAdapter<ForumItem> createAdapter(
			LinearLayoutManager layoutManager) {
		return new ThreadItemAdapter<>(this, layoutManager);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_SHARE_FORUM && result == RESULT_OK) {
			displaySnackbar(R.string.forum_shared_snackbar);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.forum_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_forum_share:
				Intent i2 = new Intent(this, ShareForumActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				startActivityForResult(i2, REQUEST_SHARE_FORUM);
				return true;
			case R.id.action_forum_sharing_status:
				Intent i3 = new Intent(this, ForumSharingStatusActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i3);
				return true;
			case R.id.action_forum_delete:
				showUnsubscribeDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected int getMaxBodyLength() {
		return MAX_FORUM_POST_BODY_LENGTH;
	}

	@Override
	@StringRes
	protected int getItemPostedString() {
		return R.string.forum_new_entry_posted;
	}

	private void showUnsubscribeDialog() {
		OnClickListener okListener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				deleteForum();
			}
		};
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setNegativeButton(R.string.dialog_button_leave, okListener);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void deleteForum() {
		forumController.deleteNamedGroup(
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void v) {
						Toast.makeText(ForumActivity.this,
								R.string.forum_left_toast, LENGTH_SHORT).show();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@Override
	public void onForumLeft(ContactId c) {
		sharingController.remove(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

}
