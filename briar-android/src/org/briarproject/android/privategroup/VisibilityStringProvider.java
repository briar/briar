package org.briarproject.android.privategroup;

import android.support.annotation.StringRes;

import org.briarproject.R;
import org.briarproject.api.privategroup.Visibility;

public class VisibilityStringProvider {

	@StringRes
	public static int getVisibilityString(Visibility v) {
		switch (v) {
			case VISIBLE:
				return R.string.groups_reveal_visible;
			case REVEALED_BY_US:
				return R.string.groups_reveal_visible_revealed_by_us;
			case REVEALED_BY_CONTACT:
				return R.string.groups_reveal_visible_revealed_by_contact;
			case INVISIBLE:
				return R.string.groups_reveal_invisible;
			default:
				throw new IllegalArgumentException("Unknown visibility");
		}
	}

}
