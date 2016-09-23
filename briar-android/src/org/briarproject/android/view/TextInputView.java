package org.briarproject.android.view;

import android.content.Context;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.R;
import org.thoughtcrime.securesms.components.KeyboardAwareRelativeLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;

import java.util.logging.Logger;

import static android.content.Context.INPUT_METHOD_SERVICE;

@UiThread
public class TextInputView extends KeyboardAwareRelativeLayout
		implements EmojiEventListener {

	private static final String TAG = TextInputView.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	private EmojiEditText editText;
	private View sendButton;
	private EmojiDrawer emojiDrawer;

	private TextInputListener listener;

	public TextInputView(Context context) {
		this(context, null);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.text_input_view, this, true);

		// find views
		EmojiToggle emojiToggle = (EmojiToggle) findViewById(R.id.emoji_toggle);
		editText = (EmojiEditText) findViewById(R.id.input_text);
		emojiDrawer = (EmojiDrawer) findViewById(R.id.emoji_drawer);
		sendButton = findViewById(R.id.btn_send);

		emojiToggle.attach(emojiDrawer);
		emojiToggle.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onEmojiToggleClicked();
			}
		});
		editText.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showSoftKeyboard();
			}
		});
		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (listener != null) {
					listener.onSendClick(editText.getText().toString());
					editText.setText("");
				}
			}
		});
		emojiDrawer.setEmojiEventListener(this);
	}

	@Override
	public void onKeyEvent(KeyEvent keyEvent) {
		editText.dispatchKeyEvent(keyEvent);
	}

	@Override
	public void onEmojiSelected(String emoji) {
		editText.insertEmoji(emoji);
	}

	private void onEmojiToggleClicked() {
		if (isEmojiDrawerOpen()) {
			showSoftKeyboard();
		} else {
			showEmojiDrawer();
		}
	}

	public void setText(String text) {
		editText.setText(text);
	}

	public void setHint(@StringRes int res) {
		editText.setHint(res);
	}

	public void setSendButtonEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
	}

	public void setListener(TextInputListener listener) {
		this.listener = listener;
	}

	public interface TextInputListener {
		void onSendClick(String text);
	}

	public void showSoftKeyboard() {
		if (isKeyboardOpen()) return;

		if (emojiDrawer.isShowing()) {
			postOnKeyboardOpen(new Runnable() {
				@Override
				public void run() {
					hideEmojiDrawer();
				}
			});
		}
		editText.post(new Runnable() {
			@Override
			public void run() {
				editText.requestFocus();
				Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
				((InputMethodManager) o).showSoftInput(editText, 0);
			}
		});
	}

	public void hideSoftKeyboard() {
		IBinder token = editText.getWindowToken();
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}

	public void showEmojiDrawer() {
		if (isKeyboardOpen()) {
			postOnKeyboardClose(new Runnable() {
				@Override public void run() {
					emojiDrawer.show(getKeyboardHeight());
				}
			});
			hideSoftKeyboard();
		} else {
			emojiDrawer.show(getKeyboardHeight());
		}
	}

	public void hideEmojiDrawer() {
		emojiDrawer.hide();
	}

	public boolean isEmojiDrawerOpen() {
		return emojiDrawer.isShowing();
	}

}
