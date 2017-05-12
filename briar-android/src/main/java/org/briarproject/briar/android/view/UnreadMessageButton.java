package org.briarproject.briar.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import javax.annotation.Nullable;

@UiThread
@NotNullByDefault
public class UnreadMessageButton extends FrameLayout {

	private final static int UP = 0, DOWN = 1;

	private final FloatingActionButton fab;
	private final TextView unread;

	public UnreadMessageButton(Context context) {
		this(context, null);
	}

	public UnreadMessageButton(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public UnreadMessageButton(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.unread_message_button, this, true);

		fab = (FloatingActionButton) findViewById(R.id.fab);
		unread = (TextView) findViewById(R.id.unreadCountView);

		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.UnreadMessageButton);
		int direction = attributes
				.getInteger(R.styleable.UnreadMessageButton_direction, DOWN);
		setDirection(direction);
		attributes.recycle();

		setUnreadCount(0);
	}

	private void setDirection(int direction) {
		if (direction == UP) {
			fab.setImageResource(R.drawable.chevron_up_white);
		} else if (direction == DOWN) {
			fab.setImageResource(R.drawable.chevron_down_white);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public void setUnreadCount(int count) {
		if (count == 0) {
			setVisibility(INVISIBLE);
		} else {
			// FIXME: Use animations when upgrading to support library 24.2.0
			//        https://code.google.com/p/android/issues/detail?id=216469
			setVisibility(VISIBLE);
			unread.setText(String.valueOf(count));
		}
	}

}
