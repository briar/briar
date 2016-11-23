package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.ImageButton;

import org.briarproject.briar.R;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiDrawerListener;

import javax.annotation.Nullable;

@UiThread
public class EmojiToggle extends ImageButton implements EmojiDrawerListener {

	private final Drawable emojiToggle;
	private final Drawable imeToggle;

	public EmojiToggle(Context context) {
		this(context, null);
	}

	public EmojiToggle(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiToggle(Context context, @Nullable AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);

		emojiToggle = ContextCompat
				.getDrawable(getContext(), R.drawable.ic_emoji_toggle);
		imeToggle = ContextCompat
				.getDrawable(getContext(), R.drawable.ic_keyboard_black);
		setToEmoji();
	}

	public void setToEmoji() {
		setImageDrawable(emojiToggle);
	}

	public void setToIme() {
		setImageDrawable(imeToggle);
	}

	public void attach(EmojiDrawer drawer) {
		drawer.setDrawerListener(this);
	}

	@Override
	public void onShown() {
		setToIme();
	}

	@Override
	public void onHidden() {
		setToEmoji();
	}
}
