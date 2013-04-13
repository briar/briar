package net.sf.briar.android.groups;

import java.util.Comparator;

import net.sf.briar.api.messaging.Group;

class GroupNameComparator implements Comparator<Group> {

	static final GroupNameComparator INSTANCE = new GroupNameComparator();

	public int compare(Group a, Group b) {
		return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
	}
}
