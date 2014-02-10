package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import org.briarproject.android.util.LayoutUtils;

import android.content.Context;
import android.widget.LinearLayout;

abstract class AddContactView extends LinearLayout {

	protected final int pad;

	protected AddContactActivity container = null;

	AddContactView(Context ctx) {
		super(ctx);
		pad = LayoutUtils.getPadding(ctx);
	}

	void init(AddContactActivity container) {
		this.container = container;
		setLayoutParams(MATCH_MATCH);
		setOrientation(VERTICAL);
		setGravity(CENTER_HORIZONTAL);
		populate();
	}

	abstract void populate();
}
