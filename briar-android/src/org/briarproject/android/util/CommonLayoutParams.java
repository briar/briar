package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class CommonLayoutParams {

	public static final ViewGroup.LayoutParams MATCH_MATCH =
			new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT);

	public static final ViewGroup.LayoutParams MATCH_WRAP =
			new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT);

	public static final ViewGroup.LayoutParams WRAP_WRAP =
			new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);

	public static final LinearLayout.LayoutParams MATCH_WRAP_1 =
			new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1);

	public static final LinearLayout.LayoutParams WRAP_WRAP_1 =
			new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);

	public static RelativeLayout.LayoutParams wrapWrap() {
		return new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
	}
}
