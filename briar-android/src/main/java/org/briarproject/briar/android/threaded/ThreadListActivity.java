package org.briarproject.briar.android.threaded;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.controller.SharingController;
import org.briarproject.briar.android.controller.SharingController.SharingListener;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.briar.android.threaded.ThreadListController.ThreadListDataSource;
import org.briarproject.briar.android.threaded.ThreadListController.ThreadListListener;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextInputView.TextInputListener;
import org.briarproject.briar.android.view.UnreadMessageButton;
import org.briarproject.briar.api.client.NamedGroup;
import org.briarproject.briar.api.client.PostHeader;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;

import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.support.design.widget.Snackbar.make;
import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE;
import static java.util.logging.Level.INFO;
import static org.briarproject.briar.android.threaded.ThreadItemAdapter.UnreadCount;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ThreadListActivity<G extends NamedGroup, A extends ThreadItemAdapter<I>, I extends ThreadItem, H extends PostHeader>
		extends BriarActivity
		implements ThreadListListener<H>, TextInputListener, SharingListener,
		ThreadItemListener<I>, ThreadListDataSource {

	protected static final String KEY_REPLY_ID = "replyId";

	private static final Logger LOG =
			Logger.getLogger(ThreadListActivity.class.getName());

	protected A adapter;
	protected BriarRecyclerView list;
	private LinearLayoutManager layoutManager;
	protected TextInputView textInput;
	protected GroupId groupId;
	private UnreadMessageButton upButton, downButton;
	@Nullable
	private MessageId replyId;

	protected abstract ThreadListController<G, I, H> getController();

	@Inject
	protected SharingController sharingController;

	@CallSuper
	@Override
	@SuppressWarnings("ConstantConditions")
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_threaded_conversation);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);
		getController().setGroupId(groupId);

		textInput = (TextInputView) findViewById(R.id.text_input_container);
		textInput.setListener(this);
		list = (BriarRecyclerView) findViewById(R.id.list);
		layoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(layoutManager);
		adapter = createAdapter(layoutManager);
		list.setAdapter(adapter);

		list.getRecyclerView().addOnScrollListener(
				new RecyclerView.OnScrollListener() {
					@Override
					public void onScrolled(RecyclerView recyclerView, int dx,
							int dy) {
						super.onScrolled(recyclerView, dx, dy);
						if (dx == 0 && dy == 0) {
							// scrollToPosition has been called and finished
							updateUnreadCount();
						}
					}

					@Override
					public void onScrollStateChanged(RecyclerView recyclerView,
							int newState) {
						super.onScrollStateChanged(recyclerView, newState);
						if (newState == SCROLL_STATE_IDLE) {
							updateUnreadCount();
						}
					}
				});
		upButton = (UnreadMessageButton) findViewById(R.id.upButton);
		downButton = (UnreadMessageButton) findViewById(R.id.downButton);
		upButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int position = adapter.getVisibleUnreadPosTop();
				if (position != NO_POSITION) {
					list.getRecyclerView().scrollToPosition(position);
				}
			}
		});
		downButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int position = adapter.getVisibleUnreadPosBottom();
				if (position != NO_POSITION) {
					list.getRecyclerView().scrollToPosition(position);
				}
			}
		});

		if (state != null) {
			byte[] replyIdBytes = state.getByteArray(KEY_REPLY_ID);
			if (replyIdBytes != null) replyId = new MessageId(replyIdBytes);
		}

		loadItems();
		sharingController.setSharingListener(this);
		loadSharingContacts();
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

	protected void loadNamedGroup() {
		getController().loadNamedGroup(
				new UiResultExceptionHandler<G, DbException>(this) {
					@Override
					public void onResultUi(G groupItem) {
						onNamedGroupLoaded(groupItem);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@UiThread
	protected abstract void onNamedGroupLoaded(G groupItem);

	protected void loadItems() {
		final int revision = adapter.getRevision();
		getController().loadItems(
				new UiResultExceptionHandler<ThreadItemList<I>, DbException>(
						this) {
					@Override
					public void onResultUi(ThreadItemList<I> items) {
						if (revision == adapter.getRevision()) {
							adapter.incrementRevision();
							if (items.isEmpty()) {
								list.showData();
							} else {
								initList(items);
								updateTextInput(replyId);
							}
						} else {
							LOG.info("Concurrent update, reloading");
							loadItems();
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	private void initList(final ThreadItemList<I> items) {
		adapter.setItems(items);
		MessageId messageId = items.getFirstVisibleItemId();
		if (messageId != null)
			adapter.setItemWithIdVisible(messageId);
		updateUnreadCount();
		list.showData();
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
						handleDbException(exception);
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
		ThreadItem replyItem = adapter.getHighlightedItem();
		if (replyItem != null) {
			outState.putByteArray(KEY_REPLY_ID, replyItem.getId().getBytes());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				supportFinishAfterTransition();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if (adapter.getHighlightedItem() != null) {
			updateTextInput(null);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onUnreadItemVisible(I item) {
		if (!item.isRead()) {
			item.setRead(true);
			getController().markItemRead(item);
		}
	}

	@Override
	public void onReplyClick(final I item) {
		updateTextInput(item.getId());
		if (textInput.isKeyboardOpen()) {
			scrollToItemAtTop(item);
		} else {
			// wait with scrolling until keyboard opened
			textInput.addOnKeyboardShownListener(
					new KeyboardAwareLinearLayout.OnKeyboardShownListener() {
						@Override
						public void onKeyboardShown() {
							scrollToItemAtTop(item);
							textInput.removeOnKeyboardShownListener(this);
						}
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

	private void scrollToItemAtTop(I item) {
		int position = adapter.findItemPosition(item);
		if (position != NO_POSITION) {
			layoutManager
					.scrollToPositionWithOffset(position, 0);
		}
	}

	protected void displaySnackbar(@StringRes int stringId) {
		Snackbar snackbar = make(list, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
	}

	private void updateTextInput(@Nullable MessageId replyItemId) {
		if (replyItemId != null) {
			textInput.setHint(R.string.forum_message_reply_hint);
			textInput.requestFocus();
			textInput.showSoftKeyboard();
		} else {
			textInput.setHint(R.string.forum_new_message_hint);
		}
		adapter.setHighlightedItem(replyItemId);
	}

	@Override
	public void onSendClick(String text) {
		if (text.trim().length() == 0)
			return;
		if (StringUtils.utf8IsTooLong(text, getMaxBodyLength())) {
			displaySnackbar(R.string.text_too_long);
			return;
		}
		I replyItem = adapter.getHighlightedItem();
		UiResultExceptionHandler<I, DbException> handler =
				new UiResultExceptionHandler<I, DbException>(this) {
					@Override
					public void onResultUi(I result) {
						addItem(result, true);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				};
		getController().createAndStoreMessage(text, replyItem, handler);
		textInput.hideSoftKeyboard();
		textInput.setText("");
		updateTextInput(null);
	}

	protected abstract int getMaxBodyLength();

	@Override
	public void onHeaderReceived(H header) {
		getController().loadItem(header,
				new UiResultExceptionHandler<I, DbException>(this) {
					@Override
					public void onResultUi(final I result) {
						addItem(result, false);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@Override
	public void onGroupRemoved() {
		supportFinishAfterTransition();
	}

	protected void addItem(I item, boolean isLocal) {
		adapter.incrementRevision();
		adapter.add(item);

		if (isLocal) {
			displaySnackbar(getItemPostedString());
			scrollToItemAtTop(item);
		} else {
			updateUnreadCount();
		}
	}

	private void updateUnreadCount() {
		UnreadCount unreadCount = adapter.getUnreadCount();
		if (LOG.isLoggable(INFO)) {
			LOG.info("Updating unread count: top=" + unreadCount.top +
					" bottom=" + unreadCount.bottom);
		}
		upButton.setUnreadCount(unreadCount.top);
		downButton.setUnreadCount(unreadCount.bottom);
	}

	@StringRes
	protected abstract int getItemPostedString();

}
