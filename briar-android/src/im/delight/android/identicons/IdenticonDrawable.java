package im.delight.android.identicons;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Created by saiimons on 05/10/14.
 */
public class IdenticonDrawable extends Drawable {
	private IdenticonBase mDelegate;

	private static final int CENTER_COLUMN_INDEX = 5;

	public IdenticonDrawable(byte[] toShow) {
		super();
		mDelegate = new IdenticonBase() {

			@Override
			protected int getRowCount() {
				return 9;
			}

			@Override
			protected int getColumnCount() {
				return 9;
			}

			@Override
			protected boolean isCellVisible(int row, int column) {
				return getByte(3 + row * CENTER_COLUMN_INDEX + getSymmetricColumnIndex(column)) >= 0;
			}
		};
		mDelegate.show(toShow);
	}

	@Override
	public int getIntrinsicHeight() {
		return 200;
	}

	@Override
	public int getIntrinsicWidth() {
		return 200;
	}

	@Override
	public void setBounds(Rect bounds) {
		super.setBounds(bounds);
		Log.d("IDENTICON", "SIZE : " + (bounds.right - bounds.left) + " " + (bounds.bottom - bounds.top));
		mDelegate.updateSize(bounds.right - bounds.left, bounds.bottom - bounds.top);
	}

	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		super.setBounds(left, top, right, bottom);
		mDelegate.updateSize(right - left, bottom - top);
	}

	@Override
	public void draw(Canvas canvas) {
		Log.d("IDENTICON", "DRAW IN PROGRESS");
		mDelegate.draw(canvas);
	}

	@Override
	public void setAlpha(int alpha) {

	}

	@Override
	public void setColorFilter(ColorFilter cf) {

	}

	@Override
	public int getOpacity() {
		return 0;
	}

	protected int getSymmetricColumnIndex(int row) {
		if (row < CENTER_COLUMN_INDEX) {
			return row;
		} else {
			return mDelegate.getColumnCount() - row - 1;
		}
	}
}
