package org.briarproject.briar.android.invitation;

import android.content.Context;
import android.widget.LinearLayout;

abstract class AddContactView extends LinearLayout {

	static final public int CODE_LEN = 6;
	protected AddContactActivity container = null;

	AddContactView(Context ctx) {
		super(ctx);
	}

	void init(AddContactActivity container) {
		this.container = container;
		populate();
	}

	abstract void populate();

}
