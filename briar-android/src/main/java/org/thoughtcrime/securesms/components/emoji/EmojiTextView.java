package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.ViewConfiguration;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;

import javax.annotation.Nullable;

import static android.widget.TextView.BufferType.SPANNABLE;

@UiThread
public class EmojiTextView extends AppCompatTextView {

	public EmojiTextView(Context context) {
		this(context, null);
	}

	public EmojiTextView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiTextView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		// this ensures the view is redrawn when invalidated
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}

	@Override
	public void setText(@Nullable CharSequence text, BufferType type) {
		CharSequence source =
				EmojiProvider.getInstance(getContext()).emojify(text, this);
		super.setText(source, SPANNABLE);
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		if (drawable instanceof EmojiDrawable) invalidate();
		else super.invalidateDrawable(drawable);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		// disable software layer if cache size is too small for it
		int drawingCacheSize = ViewConfiguration.get(getContext())
				.getScaledMaximumDrawingCacheSize();
		int width = right - left;
		int height = bottom - top;
		int size = width * height * 4;
		if (size > drawingCacheSize) {
			setLayerType(LAYER_TYPE_NONE, null);
		}
		super.onLayout(changed, left, top, right, bottom);
	}
}
