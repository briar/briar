package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_MATCH;
import android.content.Context;
import android.widget.LinearLayout;

abstract class AddContactView extends LinearLayout {

	protected AddContactActivity container = null;

	AddContactView(Context ctx) {
		super(ctx);
	}

	void init(AddContactActivity container) {
		this.container = container;
		setLayoutParams(MATCH_MATCH);
		setOrientation(VERTICAL);
		setGravity(CENTER_HORIZONTAL);
		populate();
	}

	abstract void populate();

	void wifiStateChanged() {}

	void bluetoothStateChanged() {}
}
