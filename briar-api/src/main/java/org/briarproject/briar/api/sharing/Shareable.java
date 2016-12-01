package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;

@NotNullByDefault
public interface Shareable {

	GroupId getId();

	Group getGroup();

	String getName();

}
