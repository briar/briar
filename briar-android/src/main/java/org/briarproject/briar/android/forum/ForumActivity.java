package org.briarproject.briar.android.forum;

import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.forum.ForumController.ForumListener;
import org.briarproject.briar.android.sharing.ForumSharingStatusActivity;
import org.briarproject.briar.android.sharing.ShareForumActivity;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.briarproject.briar.android.threaded.ThreadListActivity;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.android.threaded.ThreadListViewModel;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_SHARE_FORUM;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_TEXT_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ForumActivity extends
		ThreadListActivity<ForumPostItem, ThreadItemAdapter<ForumPostItem>>
		implements ForumListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	ForumController forumController;

	private ForumViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ForumViewModel.class);
	}

	@Override
	protected ThreadListController<ForumPostItem> getController() {
		return forumController;
	}

	@Override
	protected ThreadListViewModel<ForumPostItem> getViewModel() {
		return viewModel;
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		Toolbar toolbar = setUpCustomToolbar(false);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) {
			setTitle(groupName);
		} else {
			observeOnce(viewModel.loadForum(), this, forum ->
					setTitle(forum.getName())
			);
		}

		// Open member list on Toolbar click
		if (toolbar != null) {
			toolbar.setOnClickListener(v -> {
				Intent i1 = new Intent(ForumActivity.this,
						ForumSharingStatusActivity.class);
				i1.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i1);
			});
		}
	}

	@Override
	protected ThreadItemAdapter<ForumPostItem> createAdapter(
			LinearLayoutManager layoutManager) {
		return new ThreadItemAdapter<>(this, layoutManager);
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
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
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		int itemId = item.getItemId();
		if (itemId == R.id.action_forum_share) {
			Intent i2 = new Intent(this, ShareForumActivity.class);
			i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			i2.putExtra(GROUP_ID, groupId.getBytes());
			startActivityForResult(i2, REQUEST_SHARE_FORUM);
			return true;
		} else if (itemId == R.id.action_forum_sharing_status) {
			Intent i3 = new Intent(this, ForumSharingStatusActivity.class);
			i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			i3.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i3);
			return true;
		} else if (itemId == R.id.action_forum_delete) {
			showUnsubscribeDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected int getMaxTextLength() {
		return MAX_FORUM_POST_TEXT_LENGTH;
	}

	private void showUnsubscribeDialog() {
		OnClickListener okListener = (dialog, which) -> viewModel.deleteForum();
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setNegativeButton(R.string.dialog_button_leave, okListener);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	@Override
	public void onForumLeft(ContactId c) {
		sharingController.remove(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

}
