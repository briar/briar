package org.briarproject.briar.android.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import androidx.annotation.Nullable;

@NotNullByDefault
public class TouchInterceptingLinearLayout extends LinearLayout {

	public TouchInterceptingLinearLayout(Context context) {
		super(context);
	}

	public TouchInterceptingLinearLayout(Context context,
			@Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public TouchInterceptingLinearLayout(Context context,
			@Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(21)
	public TouchInterceptingLinearLayout(Context context, AttributeSet attrs,
			int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		onTouchEvent(e);
		return false;
	}
}
