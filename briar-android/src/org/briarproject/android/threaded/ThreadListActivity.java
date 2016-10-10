package org.briarproject.android.threaded;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.android.view.TextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.api.sync.GroupId;

import javax.inject.Inject;

import static android.support.design.widget.Snackbar.make;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public abstract class ThreadListActivity<I extends ThreadItem, A extends ThreadItemAdapter<I>>
		extends BriarActivity
		implements TextInputListener, ThreadItemListener<I> {

	protected static final String KEY_INPUT_VISIBILITY = "inputVisibility";
	protected static final String KEY_REPLY_ID = "replyId";

	@Inject
	protected AndroidNotificationManager notificationManager;

	protected A adapter;
	protected BriarRecyclerView list;
	protected TextInputView textInput;
	protected GroupId groupId;

	@CallSuper
	@Override
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		setContentView(getLayout());

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);

		textInput = (TextInputView) findViewById(R.id.text_input_container);
		textInput.setVisibility(GONE);
		textInput.setListener(this);
		list = (BriarRecyclerView) findViewById(R.id.list);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(linearLayoutManager);
		adapter = createAdapter(linearLayoutManager);
		list.setAdapter(adapter);
	}

	protected abstract @LayoutRes int getLayout();

	protected abstract A createAdapter(LinearLayoutManager layoutManager);

	@CallSuper
	@Override
	public void onResume() {
		super.onResume();
		notificationManager.blockNotification(groupId);
		list.startPeriodicUpdate();
	}

	@CallSuper
	@Override
	public void onPause() {
		super.onPause();
		notificationManager.unblockNotification(groupId);
		list.stopPeriodicUpdate();
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
		ThreadItem replyItem = adapter.getReplyItem();
		if (replyItem != null) {
			outState.putByteArray(KEY_REPLY_ID,
					replyItem.getId().getBytes());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
				supportFinishAfterTransition();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if (textInput.getVisibility() == VISIBLE) {
			textInput.setVisibility(GONE);
			adapter.setReplyItem(null);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onItemVisible(I item) {
		if (!item.isRead()) {
			item.setRead(true);
			markItemRead(item);
		}
	}

	protected abstract void markItemRead(I item);

	@Override
	public void onReplyClick(I item) {
		showTextInput(item);
	}

	protected void displaySnackbarShort(int stringId) {
		Snackbar snackbar = make(list, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
	}

	protected void showTextInput(@Nullable I replyItem) {
		// An animation here would be an overkill because of the keyboard
		// popping up.
		// only clear the text when the input container was not visible
		if (textInput.getVisibility() != VISIBLE) {
			textInput.setVisibility(VISIBLE);
			textInput.setText("");
		}
		textInput.requestFocus();
		textInput.showSoftKeyboard();
		textInput.setHint(replyItem == null ? R.string.forum_new_message_hint :
				R.string.forum_message_reply_hint);
		adapter.setReplyItem(replyItem);
	}

	@Override
	public void onSendClick(String text) {
		if (text.trim().length() == 0)
			return;
		I replyItem = adapter.getReplyItem();
		sendItem(text, replyItem);
		textInput.hideSoftKeyboard();
		textInput.setVisibility(GONE);
		adapter.setReplyItem(null);
	}

	protected abstract void sendItem(String text, I replyToItem);

	protected void addItem(final I item, boolean isLocal) {
		adapter.add(item);
		if (isLocal && adapter.isVisible(item)) {
			displaySnackbarShort(getItemPostedString());
		} else {
			Snackbar snackbar = Snackbar.make(list,
					isLocal ? getItemPostedString() : getItemReceivedString(),
					Snackbar.LENGTH_LONG);
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.setActionTextColor(ContextCompat
					.getColor(ThreadListActivity.this,
							R.color.briar_button_positive));
			snackbar.setAction(R.string.show, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					adapter.scrollTo(item);
				}
			});
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.show();
		}
	}

	protected abstract @StringRes int getItemPostedString();

	protected abstract @StringRes int getItemReceivedString();

}
