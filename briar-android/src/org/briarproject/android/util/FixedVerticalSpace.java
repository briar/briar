package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class FixedVerticalSpace extends View {

	public FixedVerticalSpace(Context ctx) {
		super(ctx);
		int height = LayoutUtils.getPadding(ctx);
		setLayoutParams(new LayoutParams(MATCH_PARENT, height));
	}
}
