package org.briarproject.android.util;

import static android.view.Gravity.CENTER;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class ListLoadingProgressBar extends LinearLayout {

	public ListLoadingProgressBar(Context ctx) {
		super(ctx);
		setLayoutParams(MATCH_WRAP_1);
		setGravity(CENTER);
		ProgressBar progress = new ProgressBar(ctx);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		addView(progress);
	}
}
