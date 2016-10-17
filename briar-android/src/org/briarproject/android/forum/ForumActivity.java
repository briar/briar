package org.briarproject.android.forum;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.SharingStatusForumActivity;
import org.briarproject.android.threaded.ThreadListActivity;
import org.briarproject.android.threaded.ThreadListController;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

public class ForumActivity extends
		ThreadListActivity<Forum, ForumItem, ForumPostHeader, NestedForumAdapter> {

	private static final int REQUEST_FORUM_SHARED = 3;

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
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		else loadNamedGroup();
	}

	@Override
	protected void onNamedGroupLoaded(Forum forum) {
		setTitle(forum.getName());
	}

	@Override
	@LayoutRes
	protected int getLayout() {
		return R.layout.activity_forum;
	}

	@Override
	protected NestedForumAdapter createAdapter(
			LinearLayoutManager layoutManager) {
		return new NestedForumAdapter(this, layoutManager);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_FORUM_SHARED && result == RESULT_OK) {
			displaySnackbarShort(R.string.forum_shared_snackbar);
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
		ActivityOptionsCompat options = makeCustomAnimation(this,
				android.R.anim.slide_in_left, android.R.anim.slide_out_right);
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_forum_compose_post:
				showTextInput(null);
				return true;
			case R.id.action_forum_share:
				Intent i2 = new Intent(this, ShareForumActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivityForResult(this, i2,
						REQUEST_FORUM_SHARED, options.toBundle());
				return true;
			case R.id.action_forum_sharing_status:
				Intent i3 = new Intent(this, SharingStatusForumActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivity(this, i3, options.toBundle());
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

	@Override
	@StringRes
	protected int getItemReceivedString() {
		return R.string.forum_new_entry_received;
	}

	private void showUnsubscribeDialog() {
		OnClickListener okListener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				deleteNamedGroup();
			}
		};
		AlertDialog.Builder builder = new AlertDialog.Builder(
				ForumActivity.this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setNegativeButton(R.string.dialog_button_leave, okListener);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void deleteNamedGroup() {
		forumController.deleteNamedGroup(
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void v) {
						Toast.makeText(ForumActivity.this,
								R.string.forum_left_toast, LENGTH_SHORT).show();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

}
