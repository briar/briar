package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputFilter;
import android.util.AttributeSet;

import org.briarproject.briar.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;

import javax.annotation.Nullable;

@UiThread
public class EmojiEditText extends AppCompatEditText {

	public EmojiEditText(Context context) {
		this(context, null);
	}

	public EmojiEditText(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, R.attr.editTextStyle);
	}

	public EmojiEditText(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		// this ensures the view is redrawn when invalidated
		setLayerType(LAYER_TYPE_SOFTWARE, null);
		setFilters(new InputFilter[] {new EmojiFilter(this)});
	}

	public void insertEmoji(String emoji) {
		final int start = getSelectionStart();
		final int end = getSelectionEnd();

		getText().replace(Math.min(start, end), Math.max(start, end), emoji);
		setSelection(start + emoji.length());
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		if (drawable instanceof EmojiDrawable) invalidate();
		else super.invalidateDrawable(drawable);
	}
}
