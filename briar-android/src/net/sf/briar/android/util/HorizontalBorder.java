package net.sf.briar.android.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;

public class HorizontalBorder extends View {

	public HorizontalBorder(Context ctx) {
		super(ctx);
		Resources res = ctx.getResources();
		int width = res.getInteger(R.integer.horizontal_border_width);
		setLayoutParams(new LayoutParams(MATCH_PARENT, width));
		setBackgroundColor(getResources().getColor(R.color.horizontal_border));
	}
}
