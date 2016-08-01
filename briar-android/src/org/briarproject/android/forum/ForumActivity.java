package org.briarproject.android.forum;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.forum.ForumController.ForumPostListener;
import org.briarproject.android.forum.NestedForumAdapter.OnNestedForumListener;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.SharingStatusForumActivity;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.android.view.TextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;

public class ForumActivity extends BriarActivity implements
		ForumPostListener, TextInputListener, OnNestedForumListener {

	static final String FORUM_NAME = "briar.FORUM_NAME";

	private static final int REQUEST_FORUM_SHARED = 3;
	private static final String KEY_INPUT_VISIBILITY = "inputVisibility";
	private static final String KEY_REPLY_ID = "replyId";

	@Inject
	AndroidNotificationManager notificationManager;

	@Inject
	protected ForumController forumController;

	// Protected access for testing
	protected NestedForumAdapter forumAdapter;

	private BriarRecyclerView recyclerView;
	private TextInputView textInput;
	private LinearLayoutManager linearLayoutManager;

	private volatile GroupId groupId = null;

	@Override
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_forum);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra(FORUM_NAME);
		if (forumName != null) setTitle(forumName);

		textInput = (TextInputView) findViewById(R.id.text_input_container);
		textInput.setVisibility(GONE);
		textInput.setListener(this);
		recyclerView =
				(BriarRecyclerView) findViewById(R.id.forum_discussion_list);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(linearLayoutManager);
		forumAdapter = new NestedForumAdapter(this, this, linearLayoutManager);
		recyclerView.setAdapter(forumAdapter);
		recyclerView.setEmptyText(R.string.no_forum_posts);

		forumController.loadForum(groupId,
				new UiResultHandler<List<ForumEntry>>(this) {
					@Override
					public void onResultUi(List<ForumEntry> result) {
						if (result != null) {
							Forum forum = forumController.getForum();
							if (forum != null) setTitle(forum.getName());
							List<ForumEntry> entries = new ArrayList<>(result);
							if (entries.isEmpty()) {
								recyclerView.showData();
							} else {
								forumAdapter.setEntries(entries);
								if (state != null) {
									byte[] replyId =
											state.getByteArray(KEY_REPLY_ID);
									if (replyId != null)
										forumAdapter.setReplyEntryById(replyId);
								}
							}
						} else {
							// TODO Improve UX ?
							finish();
						}
					}
				});
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		textInput.setVisibility(
				savedInstanceState.getBoolean(KEY_INPUT_VISIBILITY) ?
						VISIBLE : GONE);
	}


	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(KEY_INPUT_VISIBILITY,
				textInput.getVisibility() == VISIBLE);
		ForumEntry replyEntry = forumAdapter.getReplyEntry();
		if (replyEntry != null) {
			outState.putByteArray(KEY_REPLY_ID,
					replyEntry.getMessageId().getBytes());
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void displaySnackbarShort(int stringId) {
		Snackbar snackbar =
				Snackbar.make(recyclerView, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
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
	public void onBackPressed() {
		if (textInput.isEmojiDrawerOpen()) {
			textInput.hideEmojiDrawer();
		} else if (textInput.getVisibility() == VISIBLE) {
			textInput.setVisibility(GONE);
			forumAdapter.setReplyEntry(null);
		} else {
			super.onBackPressed();
		}
	}

	private void showTextInput(ForumEntry replyEntry) {
		// An animation here would be an overkill because of the keyboard
		// popping up.
		// only clear the text when the input container was not visible
		if (textInput.getVisibility() != VISIBLE) {
			textInput.setVisibility(VISIBLE);
			textInput.setText("");
		}
		textInput.requestFocus();
		textInput.setHint(replyEntry == null ? R.string.forum_new_message_hint :
				R.string.forum_message_reply_hint);
		showSoftKeyboardForced(textInput);
		forumAdapter.setReplyEntry(replyEntry);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		ActivityOptionsCompat options = ActivityOptionsCompat
				.makeCustomAnimation(this, android.R.anim.slide_in_left,
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

	@Override
	public void onResume() {
		super.onResume();
		notificationManager.blockNotification(groupId);
		notificationManager.clearForumPostNotification(groupId);
		recyclerView.startPeriodicUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
		notificationManager.unblockNotification(groupId);
		recyclerView.stopPeriodicUpdate();
	}

	@Override
	public void onSendClick(String text) {
		if (text.trim().length() == 0)
			return;
		if (forumController.getForum() == null) return;
		ForumEntry replyEntry = forumAdapter.getReplyEntry();
		UiResultHandler<ForumPost> resultHandler =
				new UiResultHandler<ForumPost>(this) {
					@Override
					public void onResultUi(ForumPost result) {
						forumController.storePost(result,
								new UiResultHandler<ForumEntry>(
										ForumActivity.this) {
									@Override
									public void onResultUi(ForumEntry result) {
										onForumEntryAdded(result, true);
									}
								});
					}
				};
		if (replyEntry == null) {
			// root post
			forumController.createPost(StringUtils.toUtf8(text), resultHandler);
		} else {
			forumController
					.createPost(StringUtils.toUtf8(text), replyEntry.getId(),
							resultHandler);
		}
		hideSoftKeyboard(textInput);
		textInput.setVisibility(GONE);
		forumAdapter.setReplyEntry(null);
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

	@Override
	public void onEntryVisible(ForumEntry forumEntry) {
		if (!forumEntry.isRead()) {
			forumEntry.setRead(true);
			forumController.entryRead(forumEntry);
		}
	}

	@Override
	public void onReplyClick(ForumEntry forumEntry) {
		showTextInput(forumEntry);
	}

	private void onForumEntryAdded(final ForumEntry entry, boolean isLocal) {
		forumAdapter.addEntry(entry);
		if (isLocal) {
			displaySnackbarShort(R.string.forum_new_entry_posted);
		} else {
			Snackbar snackbar = Snackbar.make(recyclerView,
					R.string.forum_new_entry_received, Snackbar.LENGTH_LONG);
			snackbar.setActionTextColor(ContextCompat
					.getColor(ForumActivity.this,
							R.color.briar_button_positive));
			snackbar.setAction(R.string.show, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					forumAdapter.scrollToEntry(entry);
				}
			});
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.show();
		}
	}

	@Override
	public void onExternalEntryAdded(ForumPostHeader header) {
		forumController.loadPost(header, new UiResultHandler<ForumEntry>(this) {
			@Override
			public void onResultUi(final ForumEntry result) {
				onForumEntryAdded(result, false);
			}
		});

	}
}
