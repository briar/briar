package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.LayoutParams;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class BriarRecyclerViewBehavior
		extends CoordinatorLayout.Behavior<BriarRecyclerView> {

	public BriarRecyclerViewBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onDependentViewChanged(CoordinatorLayout parent,
			BriarRecyclerView child, View snackbar) {
		setMargin(child, snackbar.getHeight());
		return true;
	}

	@Override
	public void onDependentViewRemoved(CoordinatorLayout parent,
			BriarRecyclerView child, View snackbar) {
		setMargin(child, 0);
	}

	@Override
	public boolean layoutDependsOn(CoordinatorLayout parent,
			BriarRecyclerView child, View dependency) {
		return dependency instanceof Snackbar.SnackbarLayout;
	}

	private void setMargin(View child, int margin) {
		LayoutParams params = (LayoutParams) child.getLayoutParams();
		params.setMargins(0, 0, 0, margin);
		child.setLayoutParams(params);
	}

}
