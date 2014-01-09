package org.briarproject.android.util;

import static android.graphics.drawable.ClipDrawable.HORIZONTAL;
import static android.view.Gravity.LEFT;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.widget.ProgressBar;

public class StrengthMeter extends ProgressBar {

	private static final int MAX = 100;
	private static final int RED = Color.rgb(255, 0, 0);
	private static final int ORANGE = Color.rgb(255, 160, 0);
	private static final int YELLOW = Color.rgb(250, 255, 15);
	private static final int LIME = Color.rgb(190, 255, 0);
	private static final int GREEN = Color.rgb(7, 255, 0);

	private final ShapeDrawable bar;

	public StrengthMeter(Context context) {
		super(context, null, android.R.attr.progressBarStyleHorizontal);
		bar = new ShapeDrawable(new RectShape());
		bar.getPaint().setColor(Color.RED);
		ClipDrawable progress = new ClipDrawable(bar, LEFT, HORIZONTAL);
		setProgressDrawable(progress);
		setIndeterminate(false);
	}

	@Override
	public int getMax() {
		return MAX;
	}

	public void setStrength(float strength) {
		if(strength < 0 || strength > 1) throw new IllegalArgumentException();
		int colour;
		if(strength < WEAK) colour = RED;
		else if(strength < QUITE_WEAK) colour = ORANGE;
		else if(strength < QUITE_STRONG) colour = YELLOW;
		else if(strength < STRONG) colour = LIME;
		else colour = GREEN;
		bar.getPaint().setColor(colour);
		setProgress((int) (strength * MAX));
	}
}
