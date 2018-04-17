package org.briarproject.bramble.test;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;

import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;

public abstract class ValidatorTestCase extends BrambleMockTestCase {

	protected final ClientHelper clientHelper =
			context.mock(ClientHelper.class);
	protected final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	protected final Clock clock = context.mock(Clock.class);

	protected final Group group = getGroup(getClientId());
	protected final GroupId groupId = group.getId();
	protected final byte[] descriptor = group.getDescriptor();
	protected final MessageId messageId = new MessageId(getRandomId());
	protected final long timestamp = 1234567890 * 1000L;
	protected final byte[] raw = getRandomBytes(123);
	protected final Message message =
			new Message(messageId, groupId, timestamp, raw);

}
