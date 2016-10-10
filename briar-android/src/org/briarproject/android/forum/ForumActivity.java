package org.briarproject.android.forum;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
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
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.forum.ForumController.ForumPostListener;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.SharingStatusForumActivity;
import org.briarproject.android.threaded.ThreadListActivity;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.widget.Toast.LENGTH_SHORT;

public class ForumActivity extends ThreadListActivity<ForumEntry, NestedForumAdapter>
		implements ForumPostListener {

	static final String FORUM_NAME = "briar.FORUM_NAME";

	private static final int REQUEST_FORUM_SHARED = 3;

	@Inject
	protected ForumController forumController;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		String forumName = i.getStringExtra(FORUM_NAME);
		if (forumName != null) setTitle(forumName);

		forumController.loadForum(groupId,
				new UiResultExceptionHandler<List<ForumEntry>, DbException>(
						this) {
					@Override
					public void onResultUi(List<ForumEntry> result) {
						Forum forum = forumController.getForum();
						if (forum != null) setTitle(forum.getName());
						List<ForumEntry> entries = new ArrayList<>(result);
						if (entries.isEmpty()) {
							list.showData();
						} else {
							adapter.setItems(entries);
							list.showData();
							if (state != null) {
								byte[] replyId =
										state.getByteArray(KEY_REPLY_ID);
								if (replyId != null)
									adapter.setReplyItemById(replyId);
							}
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO Improve UX ?
						finish();
					}
				});
	}

	@Override
	protected @LayoutRes int getLayout() {
		return R.layout.activity_forum;
	}

	@Override
	protected NestedForumAdapter createAdapter(
			LinearLayoutManager layoutManager) {
		return new NestedForumAdapter(this, layoutManager);
	}

	@Override
	public void onResume() {
		super.onResume();
		notificationManager.clearForumPostNotification(groupId);
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
		ActivityOptionsCompat options =
				makeCustomAnimation(this, android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_forum_compose_post:
				showTextInput(null);
				return true;
			case R.id.action_forum_share:
				Intent i2 = new Intent(this, ShareForumActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat
						.startActivityForResult(this, i2, REQUEST_FORUM_SHARED,
								options.toBundle());
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

	protected void markItemRead(ForumEntry entry) {
		forumController.entryRead(entry);
	}

	@Override
	public void onForumPostReceived(ForumPostHeader header) {
		forumController.loadPost(header,
				new UiResultExceptionHandler<ForumEntry, DbException>(this) {
					@Override
					public void onResultUi(final ForumEntry result) {
						addItem(result, false);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO add proper exception handling
					}
				});
	}

	@Override
	protected void sendItem(String text, @Nullable ForumEntry replyItem) {
		UiResultExceptionHandler<ForumEntry, DbException> handler =
				new UiResultExceptionHandler<ForumEntry, DbException>(this) {
					@Override
					public void onResultUi(ForumEntry result) {
						addItem(result, true);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO Improve UX ?
						finish();
					}
				};
		if (replyItem == null) {
			// root post
			forumController.createPost(StringUtils.toUtf8(text), handler);
		} else {
			forumController.createPost(StringUtils.toUtf8(text),
					replyItem.getId(), handler);
		}
	}

	@Override
	public void onForumRemoved() {
		supportFinishAfterTransition();
	}

	@Override
	protected int getItemPostedString() {
		return R.string.forum_new_entry_posted;
	}

	@Override
	protected int getItemReceivedString() {
		return R.string.forum_new_entry_received;
	}

	private void showUnsubscribeDialog() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						forumController.unsubscribe(
								new UiResultHandler<Boolean>(
										ForumActivity.this) {
									@Override
									public void onResultUi(Boolean result) {
										if (result) {
											Toast.makeText(ForumActivity.this,
													R.string.forum_left_toast,
													LENGTH_SHORT)
													.show();
										}
									}
								});
					}
				};
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ForumActivity.this,
						R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setPositiveButton(R.string.dialog_button_leave, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

}
