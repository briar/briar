package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout.Behavior;
import android.util.AttributeSet;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class DefaultBehavior<V extends View> extends Behavior<V> {

	public DefaultBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
}
