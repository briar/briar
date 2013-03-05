package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.widget.LinearLayout;

abstract class AddContactView extends LinearLayout {

	protected AddContactActivity container = null;

	AddContactView(Context context) {
		super(context);
	}

	void init(AddContactActivity container) {
		this.container = container;
		setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		setOrientation(VERTICAL);
		setGravity(CENTER_HORIZONTAL);
		populate();
	}

	abstract void populate();
}
