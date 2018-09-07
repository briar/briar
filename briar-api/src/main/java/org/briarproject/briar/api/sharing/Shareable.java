package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.Nameable;

@NotNullByDefault
public interface Shareable extends Nameable {

	GroupId getId();

}
