package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.Behavior;
import android.support.design.widget.CoordinatorLayout.LayoutParams;
import android.support.design.widget.Snackbar.SnackbarLayout;
import android.util.AttributeSet;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class SnackbarAwareBehavior<V extends View> extends Behavior<V> {

	public SnackbarAwareBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onDependentViewChanged(CoordinatorLayout parent,
			V child, View snackbar) {
		setMargin(child, snackbar.getHeight());
		return true;
	}

	@Override
	public void onDependentViewRemoved(CoordinatorLayout parent,
			V child, View snackbar) {
		setMargin(child, 0);
	}

	@Override
	public boolean layoutDependsOn(CoordinatorLayout parent,
			V child, View dependency) {
		return dependency instanceof SnackbarLayout;
	}

	private void setMargin(V child, int margin) {
		LayoutParams params = (LayoutParams) child.getLayoutParams();
		params.setMargins(0, 0, 0, margin);
		child.setLayoutParams(params);
	}
}
