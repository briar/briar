package net.sf.briar.android;

import java.util.Comparator;

import net.sf.briar.api.messaging.Group;

public class GroupNameComparator implements Comparator<Group> {

	public static final GroupNameComparator INSTANCE =
			new GroupNameComparator();

	public int compare(Group a, Group b) {
		return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
	}
}
