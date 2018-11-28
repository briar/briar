package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.view.ViewCompat.LAYOUT_DIRECTION_RTL;
import static org.briarproject.briar.android.conversation.ImageAdapter.isBottomRow;
import static org.briarproject.briar.android.conversation.ImageAdapter.isLeft;
import static org.briarproject.briar.android.conversation.ImageAdapter.isTopRow;
import static org.briarproject.briar.android.conversation.ImageAdapter.singleInRow;

@NotNullByDefault
class ImageItemDecoration extends ItemDecoration {

	private final int realBorderSize, border;
	private final boolean isRtl;

	public ImageItemDecoration(Context ctx) {
		Resources res = ctx.getResources();

		// for pixel perfection, add a pixel to the border if it has an odd size
		int b = res.getDimensionPixelSize(R.dimen.message_bubble_border);
		realBorderSize = b % 2 == 0 ? b : b + 1;;

		// we are applying half the border around the insides of each image
		// to prevent differently sized images looking slightly broken
		border = realBorderSize / 2;

		// find out if we are showing a RTL language
		Configuration config = res.getConfiguration();
		isRtl = SDK_INT >= 17 &&
				config.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
	}

	@Override
	public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
			State state) {
		if (state.getItemCount() == 1) return;
		int pos = parent.getChildAdapterPosition(view);
		int num = state.getItemCount();
		boolean left = isLeft(pos) ^ isRtl;
		outRect.top = isTopRow(pos) ? 0 : border;
		outRect.left = left ? 0 : border;
		outRect.right = left && !singleInRow(pos, num) ? border : 0;
		outRect.bottom = isBottomRow(pos, num) ? 0 : border;
	}

	public int getBorderSize() {
		return realBorderSize;
	}

	public boolean isRtl() {
		return isRtl;
	}

}
