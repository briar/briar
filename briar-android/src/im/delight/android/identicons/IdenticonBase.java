package im.delight.android.identicons;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.briarproject.api.crypto.CryptoComponent;

/**
 * Created by saiimons on 05/10/14.
 */
public abstract class IdenticonBase {
	private final int mRowCount;
	private final int mColumnCount;
	private final Paint mPaint;
	private volatile int mCellWidth;
	private volatile int mCellHeight;
	private volatile byte[] mHash;
	private volatile int[][] mColors;
	private volatile boolean mReady;

	public IdenticonBase() {
		mRowCount = getRowCount();
		mColumnCount = getColumnCount();
		mPaint = new Paint();

		mPaint.setStyle(Paint.Style.FILL);
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
	}

	public byte[] getHash(byte[] input) {
		return input;
	}

	protected void setupColors() {
		mColors = new int[mRowCount][mColumnCount];
		int colorVisible = getIconColor();
		int colorInvisible = getBackgroundColor();

		for (int r = 0; r < mRowCount; r++) {
			for (int c = 0; c < mColumnCount; c++) {
				if (isCellVisible(r, c)) {
					mColors[r][c] = colorVisible;
				} else {
					mColors[r][c] = colorInvisible;
				}
			}
		}
	}

	public void show(byte[] input) {
		mHash = input;

		// set up the cell colors according to the input that was provided via show(...)
		setupColors();

		// this view may now be drawn (and thus must be re-drawn)
		mReady = true;
	}

	public byte getByte(int index) {
		if (mHash == null) {
			return -128;
		} else {
			return mHash[index % mHash.length];
		}
	}

	abstract protected int getRowCount();

	abstract protected int getColumnCount();

	abstract protected boolean isCellVisible(int row, int column);

	protected int getIconColor() {
		int r = getByte(0) * 3 / 4 + 96;
		int g = getByte(1) * 3 / 4 + 96;
		int b = getByte(2) * 3 / 4 + 96;
		return Color.rgb(r, g, b);
	}

	protected int getBackgroundColor() {
		// http://www.google.com/design/spec/style/color.html#color-themes
		return Color.rgb(0xFA, 0xFA, 0xFA);
	}

	public void updateSize(int w, int h) {
		mCellWidth = w / mColumnCount;
		mCellHeight = h / mRowCount;
	}

	protected void draw(Canvas canvas) {
		if (mReady) {
			int x, y;
			for (int r = 0; r < mRowCount; r++) {
				for (int c = 0; c < mColumnCount; c++) {
					x = mCellWidth * c;
					y = mCellHeight * r;

					mPaint.setColor(mColors[r][c]);

					canvas.drawRect(x, y + mCellHeight, x + mCellWidth, y, mPaint);
				}
			}
		}
	}
}
