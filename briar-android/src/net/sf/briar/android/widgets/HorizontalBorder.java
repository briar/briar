package net.sf.briar.android.widgets;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import net.sf.briar.R;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;

public class HorizontalBorder extends View {

	private static final int LINE_WIDTH = 5;

	public HorizontalBorder(Context ctx) {
		super(ctx);
		setLayoutParams(new LayoutParams(MATCH_PARENT, LINE_WIDTH));
		setBackgroundColor(getResources().getColor(R.color.HorizontalBorder));
	}
}
