package org.briarproject;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

public abstract class ValidatorTestCase extends BriarMockTestCase {

	protected final ClientHelper clientHelper =
			context.mock(ClientHelper.class);
	protected final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	protected final Clock clock = context.mock(Clock.class);

	protected final MessageId messageId =
			new MessageId(TestUtils.getRandomId());
	protected final GroupId groupId = new GroupId(TestUtils.getRandomId());
	protected final long timestamp = 1234567890 * 1000L;
	protected final byte[] raw = TestUtils.getRandomBytes(123);
	protected final Message message =
			new Message(messageId, groupId, timestamp, raw);
	protected final ClientId clientId =
			new ClientId(TestUtils.getRandomString(123));
	protected final byte[] descriptor = TestUtils.getRandomBytes(123);
	protected final Group group = new Group(groupId, clientId, descriptor);

}
