package org.briarproject.briar.android.threaded;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MenuItem;

import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.controller.SharingController;
import org.briarproject.briar.android.controller.SharingController.SharingListener;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.briar.android.threaded.ThreadListController.ThreadListDataSource;
import org.briarproject.briar.android.threaded.ThreadListController.ThreadListListener;
import org.briarproject.briar.android.util.BriarSnackbarBuilder;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextSendController;
import org.briarproject.briar.android.view.TextSendController.SendListener;
import org.briarproject.briar.android.view.UnreadMessageButton;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.CallSuper;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListActivity<I extends ThreadItem, A extends ThreadItemAdapter<I>>
		extends BriarActivity
		implements ThreadListListener<I>, SendListener, SharingListener,
		ThreadItemListener<I>, ThreadListDataSource {

	protected static final String KEY_REPLY_ID = "replyId";

	private static final Logger LOG =
			getLogger(ThreadListActivity.class.getName());

	protected A adapter;

	private ThreadScrollListener<I> scrollListener;
	protected BriarRecyclerView list;
	private LinearLayoutManager layoutManager;
	protected TextInputView textInput;
	protected TextSendController sendController;
	protected GroupId groupId;
	@Nullable
	private Parcelable layoutManagerState;
	@Nullable
	private MessageId replyId;

	protected abstract ThreadListController<I> getController();

	protected abstract ThreadListViewModel<I> getViewModel();

	@Inject
	protected SharingController sharingController;

	@CallSuper
	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_threaded_conversation);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);
		getController().setGroupId(groupId);
		getViewModel().setGroupId(groupId);

		textInput = findViewById(R.id.text_input_container);
		sendController = new TextSendController(textInput, this, false);
		textInput.setSendController(sendController);
		textInput.setMaxTextLength(getMaxTextLength());
		textInput.setReady(true);

		UnreadMessageButton upButton = findViewById(R.id.upButton);
		UnreadMessageButton downButton = findViewById(R.id.downButton);

		list = findViewById(R.id.list);
		layoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(layoutManager);
		adapter = createAdapter(layoutManager);
		list.setAdapter(adapter);
		scrollListener = new ThreadScrollListener<>(adapter, getController(),
				upButton, downButton);
		list.getRecyclerView().addOnScrollListener(scrollListener);

		upButton.setOnClickListener(v -> {
			int position = adapter.getVisibleUnreadPosTop();
			if (position != NO_POSITION) {
				list.getRecyclerView().scrollToPosition(position);
			}
		});
		downButton.setOnClickListener(v -> {
			int position = adapter.getVisibleUnreadPosBottom();
			if (position != NO_POSITION) {
				list.getRecyclerView().scrollToPosition(position);
			}
		});

		if (state != null) {
			byte[] replyIdBytes = state.getByteArray(KEY_REPLY_ID);
			if (replyIdBytes != null) replyId = new MessageId(replyIdBytes);
		}

		getViewModel().getItems().observe(this, result -> result
				.onError(this::handleException)
				.onSuccess(this::displayItems)
		);

		sharingController.setSharingListener(this);
		loadSharingContacts();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		getViewModel().storeMessageId(getFirstVisibleMessageId());
	}

	@Override
	@Nullable
	public MessageId getFirstVisibleMessageId() {
		if (layoutManager != null && adapter != null) {
			int position =
					layoutManager.findFirstVisibleItemPosition();
			I i = adapter.getItemAt(position);
			return i == null ? null : i.getId();
		}
		return null;
	}

	protected abstract A createAdapter(LinearLayoutManager layoutManager);

	protected void displayItems(List<I> items) {
		if (items.isEmpty()) {
			list.showData();
		} else {
			adapter.submitList(items, this::scrollAfterListCommitted);
			updateTextInput();
		}
	}

	/**
	 * Scrolls to the first visible item last time the activity was open,
	 * if one exists and this is the first time, the list gets displayed.
	 * Or scrolls to a locally added item that has just been added to the list.
	 */
	private void scrollAfterListCommitted() {
		MessageId restoredFirstVisibleItemId =
				getViewModel().getAndResetRestoredMessageId();
		MessageId scrollToItem =
				getViewModel().getAndResetScrollToItem();
		if (restoredFirstVisibleItemId != null) {
			scrollToItemAtTop(restoredFirstVisibleItemId);
		} else if (scrollToItem != null) {
			scrollToItemAtTop(scrollToItem);
		}
		scrollListener.updateUnreadButtons(layoutManager);
	}

	protected void loadSharingContacts() {
		getController().loadSharingContacts(
				new UiResultExceptionHandler<Collection<ContactId>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<ContactId> contacts) {
						sharingController.addAll(contacts);
						int online = sharingController.getOnlineCount();
						setToolbarSubTitle(contacts.size(), online);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	@CallSuper
	@Override
	public void onStart() {
		super.onStart();
		sharingController.onStart();
		list.startPeriodicUpdate();
	}

	@CallSuper
	@Override
	public void onStop() {
		super.onStop();
		sharingController.onStop();
		list.stopPeriodicUpdate();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (replyId != null) {
			outState.putByteArray(KEY_REPLY_ID, replyId.getBytes());
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			supportFinishAfterTransition();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (adapter.getHighlightedItem() != null) {
			textInput.clearText();
			replyId = null;
			updateTextInput();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onReplyClick(I item) {
		replyId = item.getId();
		updateTextInput();
		// FIXME This does not work for a hardware keyboard
		if (textInput.isKeyboardOpen()) {
			scrollToItemAtTop(item.getId());
		} else {
			// wait with scrolling until keyboard opened
			textInput.setOnKeyboardShownListener(() -> {
				scrollToItemAtTop(item.getId());
				textInput.setOnKeyboardShownListener(null);
			});
		}
	}

	@Override
	public void onSharingInfoUpdated(int total, int online) {
		setToolbarSubTitle(total, online);
	}

	@Override
	public void onInvitationAccepted(ContactId c) {
		sharingController.add(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

	protected void setToolbarSubTitle(int total, int online) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(
					getString(R.string.shared_with, total, online));
		}
	}

	private void scrollToItemAtTop(MessageId messageId) {
		int position = adapter.findItemPosition(messageId);
		if (position != NO_POSITION) {
			layoutManager.scrollToPositionWithOffset(position, 0);
		}
	}

	protected void displaySnackbar(@StringRes int stringId) {
		new BriarSnackbarBuilder()
				.make(list, stringId, Snackbar.LENGTH_SHORT)
				.show();
	}

	private void updateTextInput() {
		if (replyId != null) {
			textInput.setHint(R.string.forum_message_reply_hint);
			textInput.showSoftKeyboard();
		} else {
			textInput.setHint(R.string.forum_new_message_hint);
		}
		adapter.setHighlightedItem(replyId);
	}

	@Override
	public void onSendClick(@Nullable String text,
			List<AttachmentHeader> headers) {
		if (isNullOrEmpty(text)) throw new AssertionError();

		I replyItem = adapter.getHighlightedItem();
		getViewModel().createAndStoreMessage(text, replyItem);
		textInput.hideSoftKeyboard();
		textInput.clearText();
		replyId = null;
		updateTextInput();
	}

	protected abstract int getMaxTextLength();

	@Override
	public void onItemReceived(I item) {
		getViewModel().addItem(item, false);
	}

	@Override
	public void onGroupRemoved() {
		supportFinishAfterTransition();
	}

}
