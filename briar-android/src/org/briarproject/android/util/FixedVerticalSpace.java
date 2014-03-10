package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class FixedVerticalSpace extends View {

	public FixedVerticalSpace(Context ctx) {
		super(ctx);
		setHeight(LayoutUtils.getPadding(ctx));
	}

	public void setHeight(int height) {
		setLayoutParams(new LayoutParams(WRAP_CONTENT, height));
	}
}
