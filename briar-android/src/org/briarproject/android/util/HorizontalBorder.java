package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import org.briarproject.R;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class HorizontalBorder extends View {

	public HorizontalBorder(Context ctx) {
		super(ctx);
		setLayoutParams(new LayoutParams(MATCH_PARENT, 1));
		setBackgroundColor(getResources().getColor(R.color.horizontal_border));
	}
}
