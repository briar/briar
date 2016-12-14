package org.briarproject.bramble.test;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;

public abstract class ValidatorTestCase extends BrambleMockTestCase {

	protected final ClientHelper clientHelper =
			context.mock(ClientHelper.class);
	protected final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	protected final Clock clock = context.mock(Clock.class);
	protected final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);

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
