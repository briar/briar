package net.sf.briar.android.util;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;

public class HorizontalSpace extends View {

	public HorizontalSpace(Context ctx) {
		super(ctx);
		setLayoutParams(new LayoutParams(WRAP_CONTENT, 0, 1));
	}
}
