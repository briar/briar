package org.briarproject.android.contact;

import android.view.View;

class ConversationMessageOutViewHolder extends ConversationOutItemViewHolder {

	ConversationMessageOutViewHolder(View v) {
		super(v);
	}

	@Override
	protected boolean hasDarkBackground() {
		return true;
	}

}
