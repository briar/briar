package org.briarproject.api.sharing;

import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

public interface Shareable {

	GroupId getId();

	Group getGroup();

	String getName();

}
