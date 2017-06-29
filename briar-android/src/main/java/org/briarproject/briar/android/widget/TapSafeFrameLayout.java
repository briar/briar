package org.briarproject.briar.android.widget;

import android.content.Context;
import android.support.annotation.AttrRes;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import static android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED;

@NotNullByDefault
public class TapSafeFrameLayout extends FrameLayout {

	@Nullable
	private OnTapFilteredListener listener;

	public TapSafeFrameLayout(Context context) {
		super(context);
		setFilterTouchesWhenObscured(false);
	}

	public TapSafeFrameLayout(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		setFilterTouchesWhenObscured(false);
	}

	public TapSafeFrameLayout(Context context, @Nullable AttributeSet attrs,
			@AttrRes int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setFilterTouchesWhenObscured(false);
	}

	public void setOnTapFilteredListener(OnTapFilteredListener listener) {
		this.listener = listener;
	}

	@Override
	public boolean onFilterTouchEventForSecurity(MotionEvent e) {
		boolean filter = (e.getFlags() & FLAG_WINDOW_IS_OBSCURED) != 0;
		if (filter && listener != null) listener.onTapFiltered();
		return !filter;
	}

	public interface OnTapFilteredListener {
		void onTapFiltered();
	}
}
