package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import org.briarproject.briar.R;

import javax.annotation.Nullable;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.Align.CENTER;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

@UiThread
public class EmojiView extends View implements Drawable.Callback {

	private final Paint paint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);

	private String emoji;
	private Drawable drawable;

	public EmojiView(Context context) {
		this(context, null);
	}

	public EmojiView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setEmoji(String emoji) {
		this.emoji = emoji;
		this.drawable = EmojiProvider.getInstance(getContext())
				.getEmojiDrawable(Character.codePointAt(emoji, 0));
		postInvalidate();
	}

	public String getEmoji() {
		return emoji;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (drawable != null) {
			drawable.setBounds(getPaddingLeft(),
					getPaddingTop(),
					getWidth() - getPaddingRight(),
					getHeight() - getPaddingBottom());
			drawable.setCallback(this);
			drawable.draw(canvas);
		} else {
			float targetFontSize =
					0.75f * getHeight() - getPaddingTop() - getPaddingBottom();
			paint.setTextSize(targetFontSize);
			paint.setColor(ContextCompat
					.getColor(getContext(), R.color.emoji_text_color));
			paint.setTextAlign(CENTER);
			int xPos = (canvas.getWidth() / 2);
			int yPos = (int) ((canvas.getHeight() / 2) -
					((paint.descent() + paint.ascent()) / 2));

			float overflow = paint.measureText(emoji) /
					(getWidth() - getPaddingLeft() - getPaddingRight());
			if (overflow > 1f) {
				paint.setTextSize(targetFontSize / overflow);
				yPos = (int) ((canvas.getHeight() / 2) -
						((paint.descent() + paint.ascent()) / 2));
			}
			canvas.drawText(emoji, xPos, yPos, paint);
		}
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		super.invalidateDrawable(drawable);
		postInvalidate();
	}
}
