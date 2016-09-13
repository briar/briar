package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;

@UiThread
public class EmojiTextView extends TextView {

	private CharSequence source;
	private boolean needsEllipsizing;

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
		source = EmojiProvider.getInstance(getContext()).emojify(text, this);
		setTextEllipsized(source);
	}

	private void setTextEllipsized(final @Nullable CharSequence source) {
		super.setText(
				needsEllipsizing ? ellipsize(source) : source,
				BufferType.SPANNABLE);
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		if (drawable instanceof EmojiDrawable) invalidate();
		else super.invalidateDrawable(drawable);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int size = MeasureSpec.getSize(widthMeasureSpec);
		final int mode = MeasureSpec.getMode(widthMeasureSpec);
		if (getEllipsize() == TruncateAt.END &&
				!TextUtils.isEmpty(source) &&
				(mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) &&
				getPaint().breakText(source, 0, source.length() - 1, true, size,
						null) != source.length()) {
			needsEllipsizing = true;
			FontMetricsInt font = getPaint().getFontMetricsInt();
			super.onMeasure(
					MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
					MeasureSpec
							.makeMeasureSpec(Math.abs(font.top - font.bottom),
									MeasureSpec.EXACTLY));
		} else {
			needsEllipsizing = false;
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		if (changed) setTextEllipsized(source);
		super.onLayout(changed, left, top, right, bottom);
	}

	@Nullable
	public CharSequence ellipsize(@Nullable CharSequence text) {
		if (TextUtils.isEmpty(text) || getWidth() == 0 ||
				getEllipsize() != TruncateAt.END) {
			return text;
		} else {
			return TextUtils.ellipsize(text,
					getPaint(),
					getWidth() - getPaddingRight() - getPaddingLeft(),
					TruncateAt.END);
		}
	}

}
