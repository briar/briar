package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class FixedVerticalSpace extends View {

	public FixedVerticalSpace(Context ctx) {
		super(ctx);
		int pad = LayoutUtils.getPadding(ctx);
		setLayoutParams(new LayoutParams(WRAP_CONTENT, pad));
	}
}
