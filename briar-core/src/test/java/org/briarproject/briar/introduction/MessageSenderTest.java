package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.test.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.junit.Assert.assertFalse;

public class MessageSenderTest extends BriarTestCase {

	private final Mockery context;
	private final MessageSender messageSender;
	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final MetadataEncoder metadataEncoder;
	private final MessageQueueManager messageQueueManager;
	private final Clock clock;

	public MessageSenderTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		clientHelper = context.mock(ClientHelper.class);
		metadataEncoder =
				context.mock(MetadataEncoder.class);
		messageQueueManager =
				context.mock(MessageQueueManager.class);
		clock = context.mock(Clock.class);

		messageSender = new MessageSender(db, clientHelper, clock,
				metadataEncoder, messageQueueManager);
	}

	@Test
	public void testSendMessage() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		final Group privateGroup =
				new Group(new GroupId(TestUtils.getRandomId()),
						new ClientId(TestUtils.getRandomString(5)),
						new byte[0]);
		final SessionId sessionId = new SessionId(TestUtils.getRandomId());
		byte[] mac = TestUtils.getRandomBytes(42);
		byte[] sig = TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH);
		final long time = 42L;
		final BdfDictionary msg = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_ACK),
				new BdfEntry(GROUP_ID, privateGroup.getId()),
				new BdfEntry(SESSION_ID, sessionId),
				new BdfEntry(MAC, mac),
				new BdfEntry(SIGNATURE, sig)
		);
		final BdfList bodyList =
				BdfList.of(TYPE_ACK, sessionId.getBytes(), mac, sig);
		final byte[] body = TestUtils.getRandomBytes(8);
		final Metadata metadata = new Metadata();

		context.checking(new Expectations() {{
			oneOf(clientHelper).toByteArray(bodyList);
			will(returnValue(body));
			oneOf(db).getGroup(txn, privateGroup.getId());
			will(returnValue(privateGroup));
			oneOf(metadataEncoder).encode(msg);
			will(returnValue(metadata));
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
			oneOf(messageQueueManager)
					.sendMessage(txn, privateGroup, time, body, metadata);
		}});

		messageSender.sendMessage(txn, msg);

		context.assertIsSatisfied();
		assertFalse(txn.isCommitted());
	}

}
