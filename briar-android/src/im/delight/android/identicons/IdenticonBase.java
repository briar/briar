package im.delight.android.identicons;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.briarproject.api.crypto.CryptoComponent;

/**
 * Created by saiimons on 05/10/14.
 */
public abstract class IdenticonBase {
    private final CryptoComponent mCrypto;
    private final int mRowCount;
    private final int mColumnCount;
    private final Paint mPaint;
    private volatile int mCellWidth;
    private volatile int mCellHeight;
    private volatile byte[] mHash;
    private volatile int[][] mColors;
    private volatile boolean mReady;

    public IdenticonBase() {
        mCrypto = getCrypto();
        mRowCount = getRowCount();
        mColumnCount = getColumnCount();
        mPaint = new Paint();

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
    }

    public byte[] getHash(byte[] input) {
        byte[] mHash;
        // if the input was null
        if (input == null) {
            // we can't create a hash value and have nothing to show (draw to the view)
            mHash = null;
        } else {
            // generate a hash from the input to get unique but deterministic byte values
            try {
                mHash = mCrypto.hash(input);
            } catch (Exception e) {
                mHash = null;
            }
        }
        return mHash;
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
        if(input != null) {
            mHash = getHash(input);
        } else {
            mHash = null;
        }
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

    abstract protected CryptoComponent getCrypto();

    abstract protected int getRowCount();

    abstract protected int getColumnCount();

    abstract protected boolean isCellVisible(int row, int column);

    abstract protected int getIconColor();

    protected int getBackgroundColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(getIconColor(), hsv);
        if (hsv[2] < 0.5)
            return Color.parseColor("#ffeeeeee"); // @color/background_material_light
        else
            return Color.parseColor("#ff303030"); // @color/background_material_dark
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
