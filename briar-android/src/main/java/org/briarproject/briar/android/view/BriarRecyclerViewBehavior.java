package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.View;

public class BriarRecyclerViewBehavior
		extends CoordinatorLayout.Behavior<BriarRecyclerView> {

	public BriarRecyclerViewBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onDependentViewChanged(CoordinatorLayout parent,
			BriarRecyclerView child, View dependency) {

		// FIXME the below code works, but does not reset margin when snackbar is dismissed
/*
		int margin = 0;
		if (dependency.isShown()) margin = dependency.getHeight();

		// set snackbar height as bottom margin if it is shown
		CoordinatorLayout.LayoutParams params =
				(CoordinatorLayout.LayoutParams) child.getLayoutParams();
		params.setMargins(0, 0, 0, margin);
		child.setLayoutParams(params);

		child.scrollToPosition(0);
*/
		return true;
	}

	@Override
	public boolean layoutDependsOn(CoordinatorLayout parent,
			BriarRecyclerView child, View dependency) {
		// we only want to trigger the change
		// only when the changes is from a snackbar
		return dependency instanceof Snackbar.SnackbarLayout;
	}

}
