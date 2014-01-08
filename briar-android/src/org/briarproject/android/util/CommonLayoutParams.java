package org.briarproject.android.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.widget.LinearLayout;

public class CommonLayoutParams {

	public static final LinearLayout.LayoutParams MATCH_MATCH =
			new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);

	public static final LinearLayout.LayoutParams MATCH_WRAP =
			new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);

	public static final LinearLayout.LayoutParams MATCH_WRAP_1 =
			new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1);

	public static final LinearLayout.LayoutParams WRAP_WRAP =
			new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);

	public static final LinearLayout.LayoutParams WRAP_WRAP_1 =
			new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1);
}
