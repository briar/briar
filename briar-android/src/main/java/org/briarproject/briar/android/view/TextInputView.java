package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.briar.R;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;

import javax.annotation.Nullable;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;

@UiThread
public class TextInputView extends KeyboardAwareLinearLayout
		implements EmojiEventListener {

	protected final ViewHolder ui;
	protected TextInputListener listener;

	public TextInputView(Context context) {
		this(context, null);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setOrientation(VERTICAL);
		setLayoutTransition(new LayoutTransition());

		inflateLayout(context);
		ui = new ViewHolder();
		setUpViews(context, attrs);
	}

	protected void inflateLayout(Context context) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.text_input_view, this, true);
	}

	@CallSuper
	protected void setUpViews(Context context, @Nullable AttributeSet attrs) {
		// get attributes
		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.TextInputView);
		String hint = attributes.getString(R.styleable.TextInputView_hint);
		attributes.recycle();

		if (hint != null) {
			ui.editText.setHint(hint);
		}

		ui.emojiToggle.attach(ui.emojiDrawer);
		ui.emojiToggle.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onEmojiToggleClicked();
			}
		});
		ui.editText.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showSoftKeyboard();
			}
		});
		ui.editText.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KEYCODE_BACK && isEmojiDrawerOpen()) {
					hideEmojiDrawer();
					return true;
				}
				return false;
			}
		});
		ui.sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (listener != null) {
					listener.onSendClick(ui.editText.getText().toString());
				}
			}
		});
		ui.emojiDrawer.setEmojiEventListener(this);
	}

	@Override
	public void setVisibility(int visibility) {
		if (visibility == GONE && isKeyboardOpen()) {
			onKeyboardClose();
		}
		super.setVisibility(visibility);
	}

	@Override
	public void onKeyEvent(KeyEvent keyEvent) {
		ui.editText.dispatchKeyEvent(keyEvent);
	}

	@Override
	public void onEmojiSelected(String emoji) {
		ui.editText.insertEmoji(emoji);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return ui.editText.requestFocus(direction, previouslyFocusedRect);
	}

	private void onEmojiToggleClicked() {
		if (isEmojiDrawerOpen()) {
			showSoftKeyboard();
		} else {
			showEmojiDrawer();
		}
	}

	public void setText(String text) {
		ui.editText.setText(text);
	}

	public Editable getText() {
		return ui.editText.getText();
	}

	public void setHint(@StringRes int res) {
		ui.editText.setHint(res);
	}

	public void setSendButtonEnabled(boolean enabled) {
		ui.sendButton.setEnabled(enabled);
	}

	public void addTextChangedListener(TextWatcher watcher) {
		ui.editText.addTextChangedListener(watcher);
	}

	public void setListener(TextInputListener listener) {
		this.listener = listener;
	}

	public void showSoftKeyboard() {
		if (isKeyboardOpen()) return;

		if (ui.emojiDrawer.isShowing()) {
			postOnKeyboardOpen(new Runnable() {
				@Override
				public void run() {
					hideEmojiDrawer();
				}
			});
		}
		ui.editText.post(new Runnable() {
			@Override
			public void run() {
				ui.editText.requestFocus();
				InputMethodManager imm =
						(InputMethodManager) getContext()
								.getSystemService(INPUT_METHOD_SERVICE);
				imm.showSoftInput(ui.editText, SHOW_IMPLICIT);
			}
		});
	}

	public void hideSoftKeyboard() {
		IBinder token = ui.editText.getWindowToken();
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}

	public void showEmojiDrawer() {
		if (isKeyboardOpen()) {
			postOnKeyboardClose(new Runnable() {
				@Override public void run() {
					ui.emojiDrawer.show(getKeyboardHeight());
				}
			});
			hideSoftKeyboard();
		} else {
			ui.emojiDrawer.show(getKeyboardHeight());
			ui.editText.requestFocus();
		}
	}

	public void hideEmojiDrawer() {
		ui.emojiDrawer.hide();
	}

	public boolean isEmojiDrawerOpen() {
		return ui.emojiDrawer.isShowing();
	}

	protected class ViewHolder {

		private final EmojiToggle emojiToggle;
		final EmojiEditText editText;
		final View sendButton;
		final EmojiDrawer emojiDrawer;

		private ViewHolder() {
			emojiToggle = (EmojiToggle) findViewById(R.id.emoji_toggle);
			editText = (EmojiEditText) findViewById(R.id.input_text);
			emojiDrawer = (EmojiDrawer) findViewById(R.id.emoji_drawer);
			sendButton = findViewById(R.id.btn_send);
		}
	}

	public interface TextInputListener {
		void onSendClick(String text);
	}

}
