package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class FixedHorizontalSpace extends View {

	public FixedHorizontalSpace(Context ctx) {
		super(ctx);
	}

	public void setWidth(int width) {
		setLayoutParams(new LayoutParams(width, WRAP_CONTENT));
	}
}
