package org.briarproject.clients;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager.IncomingQueueMessageHook;
import org.briarproject.api.clients.MessageQueueManager.QueueMessageValidator;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.clients.QueueMessageFactory;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.api.sync.ValidationManager.MessageValidator;
import org.briarproject.util.ByteUtils;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.briarproject.api.clients.MessageQueueManager.QUEUE_STATE_KEY;
import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MessageQueueManagerImplTest extends BriarTestCase {

	private final GroupId groupId = new GroupId(TestUtils.getRandomId());
	private final ClientId clientId = new ClientId(TestUtils.getRandomId());
	private final byte[] descriptor = new byte[MAX_GROUP_DESCRIPTOR_LENGTH];
	private final Group group = new Group(groupId, clientId, descriptor);
	private final long timestamp = System.currentTimeMillis();

	@Test
	public void testSendingMessages() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final Transaction txn = new Transaction(null);
		final byte[] body = new byte[123];
		final Metadata groupMetadata = new Metadata();
		final Metadata messageMetadata = new Metadata();
		final Metadata groupMetadata1 = new Metadata();
		final byte[] queueState = new byte[123];
		groupMetadata1.put(QUEUE_STATE_KEY, queueState);
		context.checking(new Expectations() {{
			// First message: queue state does not exist
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata));
			oneOf(clientHelper).toByteArray(with(any(BdfDictionary.class)));
			will(new EncodeQueueStateAction(1L, 0L, new BdfList()));
			oneOf(db).mergeGroupMetadata(with(txn), with(groupId),
					with(any(Metadata.class)));
			oneOf(queueMessageFactory).createMessage(groupId, timestamp, 0L,
					body);
			will(new CreateMessageAction());
			oneOf(db).addLocalMessage(with(txn), with(any(QueueMessage.class)),
					with(clientId), with(messageMetadata), with(true));
			// Second message: queue state exists
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata1));
			oneOf(clientHelper).toDictionary(queueState, 0, queueState.length);
			will(new DecodeQueueStateAction(1L, 0L, new BdfList()));
			oneOf(clientHelper).toByteArray(with(any(BdfDictionary.class)));
			will(new EncodeQueueStateAction(2L, 0L, new BdfList()));
			oneOf(db).mergeGroupMetadata(with(txn), with(groupId),
					with(any(Metadata.class)));
			oneOf(queueMessageFactory).createMessage(groupId, timestamp, 1L,
					body);
			will(new CreateMessageAction());
			oneOf(db).addLocalMessage(with(txn), with(any(QueueMessage.class)),
					with(clientId), with(messageMetadata), with(true));
		}});

		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// First message
		QueueMessage q = mqm.sendMessage(txn, group, timestamp, body,
				messageMetadata);
		assertEquals(groupId, q.getGroupId());
		assertEquals(timestamp, q.getTimestamp());
		assertEquals(0L, q.getQueuePosition());
		assertEquals(QUEUE_MESSAGE_HEADER_LENGTH + body.length, q.getLength());

		// Second message
		QueueMessage q1 = mqm.sendMessage(txn, group, timestamp, body,
				messageMetadata);
		assertEquals(groupId, q1.getGroupId());
		assertEquals(timestamp, q1.getTimestamp());
		assertEquals(1L, q1.getQueuePosition());
		assertEquals(QUEUE_MESSAGE_HEADER_LENGTH + body.length, q1.getLength());

		context.assertIsSatisfied();
	}

	@Test
	public void testValidatorRejectsShortMessage() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<MessageValidator> captured =
				new AtomicReference<MessageValidator>();
		final QueueMessageValidator queueMessageValidator =
				context.mock(QueueMessageValidator.class);
		// The message is too short to be a valid queue message
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH - 1];
		final Message message = new Message(messageId, groupId, timestamp, raw);
		context.checking(new Expectations() {{
			oneOf(validationManager).registerMessageValidator(with(clientId),
					with(any(MessageValidator.class)));
			will(new CaptureArgumentAction<MessageValidator>(captured,
					MessageValidator.class, 1));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating message validator
		mqm.registerMessageValidator(clientId, queueMessageValidator);
		MessageValidator delegate = captured.get();
		assertNotNull(delegate);
		// The message should be invalid
		assertNull(delegate.validateMessage(message, group));

		context.assertIsSatisfied();
	}

	@Test
	public void testValidatorRejectsNegativeQueuePosition() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<MessageValidator> captured =
				new AtomicReference<MessageValidator>();
		final QueueMessageValidator queueMessageValidator =
				context.mock(QueueMessageValidator.class);
		// The message has a negative queue position
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		for (int i = 0; i < 8; i++)
			raw[MESSAGE_HEADER_LENGTH + i] = (byte) 0xFF;
		final Message message = new Message(messageId, groupId, timestamp, raw);
		context.checking(new Expectations() {{
			oneOf(validationManager).registerMessageValidator(with(clientId),
					with(any(MessageValidator.class)));
			will(new CaptureArgumentAction<MessageValidator>(captured,
					MessageValidator.class, 1));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating message validator
		mqm.registerMessageValidator(clientId, queueMessageValidator);
		MessageValidator delegate = captured.get();
		assertNotNull(delegate);
		// The message should be invalid
		assertNull(delegate.validateMessage(message, group));

		context.assertIsSatisfied();
	}

	@Test
	public void testValidatorDelegatesValidMessage() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<MessageValidator> captured =
				new AtomicReference<MessageValidator>();
		final QueueMessageValidator queueMessageValidator =
				context.mock(QueueMessageValidator.class);
		final Metadata messageMetadata = new Metadata();
		// The message is valid, with a queue position of zero
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		final Message message = new Message(messageId, groupId, timestamp, raw);
		context.checking(new Expectations() {{
			oneOf(validationManager).registerMessageValidator(with(clientId),
					with(any(MessageValidator.class)));
			will(new CaptureArgumentAction<MessageValidator>(captured,
					MessageValidator.class, 1));
			// The message should be delegated
			oneOf(queueMessageValidator).validateMessage(
					with(any(QueueMessage.class)), with(group));
			will(returnValue(messageMetadata));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating message validator
		mqm.registerMessageValidator(clientId, queueMessageValidator);
		MessageValidator delegate = captured.get();
		assertNotNull(delegate);
		// The message should be valid and the metadata should be returned
		assertSame(messageMetadata, delegate.validateMessage(message, group));

		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessageHookDeletesDuplicateMessage()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<IncomingMessageHook> captured =
				new AtomicReference<IncomingMessageHook>();
		final IncomingQueueMessageHook incomingQueueMessageHook =
				context.mock(IncomingQueueMessageHook.class);
		final Transaction txn = new Transaction(null);
		final Metadata groupMetadata = new Metadata();
		final byte[] queueState = new byte[123];
		groupMetadata.put(QUEUE_STATE_KEY, queueState);
		// The message has queue position 0
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		final Message message = new Message(messageId, groupId, timestamp, raw);
		context.checking(new Expectations() {{
			oneOf(validationManager).registerIncomingMessageHook(with(clientId),
					with(any(IncomingMessageHook.class)));
			will(new CaptureArgumentAction<IncomingMessageHook>(captured,
					IncomingMessageHook.class, 1));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata));
			// Queue position 1 is expected
			oneOf(clientHelper).toDictionary(queueState, 0, queueState.length);
			will(new DecodeQueueStateAction(0L, 1L, new BdfList()));
			// The message and its metadata should be deleted
			oneOf(db).deleteMessage(txn, messageId);
			oneOf(db).deleteMessageMetadata(txn, messageId);
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating incoming message hook
		mqm.registerIncomingMessageHook(clientId, incomingQueueMessageHook);
		IncomingMessageHook delegate = captured.get();
		assertNotNull(delegate);
		// Pass the message to the hook
		delegate.incomingMessage(txn, message, new Metadata());

		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessageHookAddsOutOfOrderMessageToPendingList()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<IncomingMessageHook> captured =
				new AtomicReference<IncomingMessageHook>();
		final IncomingQueueMessageHook incomingQueueMessageHook =
				context.mock(IncomingQueueMessageHook.class);
		final Transaction txn = new Transaction(null);
		final Metadata groupMetadata = new Metadata();
		final byte[] queueState = new byte[123];
		groupMetadata.put(QUEUE_STATE_KEY, queueState);
		// The message has queue position 1
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		ByteUtils.writeUint64(1L, raw, MESSAGE_HEADER_LENGTH);
		final Message message = new Message(messageId, groupId, timestamp, raw);
		final BdfList pending = BdfList.of(BdfList.of(1L, messageId));
		context.checking(new Expectations() {{
			oneOf(validationManager).registerIncomingMessageHook(with(clientId),
					with(any(IncomingMessageHook.class)));
			will(new CaptureArgumentAction<IncomingMessageHook>(captured,
					IncomingMessageHook.class, 1));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata));
			// Queue position 0 is expected
			oneOf(clientHelper).toDictionary(queueState, 0, queueState.length);
			will(new DecodeQueueStateAction(0L, 0L, new BdfList()));
			// The message should be added to the pending list
			oneOf(clientHelper).toByteArray(with(any(BdfDictionary.class)));
			will(new EncodeQueueStateAction(0L, 0L, pending));
			oneOf(db).mergeGroupMetadata(with(txn), with(groupId),
					with(any(Metadata.class)));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating incoming message hook
		mqm.registerIncomingMessageHook(clientId, incomingQueueMessageHook);
		IncomingMessageHook delegate = captured.get();
		assertNotNull(delegate);
		// Pass the message to the hook
		delegate.incomingMessage(txn, message, new Metadata());

		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessageHookDelegatesInOrderMessage()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<IncomingMessageHook> captured =
				new AtomicReference<IncomingMessageHook>();
		final IncomingQueueMessageHook incomingQueueMessageHook =
				context.mock(IncomingQueueMessageHook.class);
		final Transaction txn = new Transaction(null);
		final Metadata groupMetadata = new Metadata();
		final byte[] queueState = new byte[123];
		groupMetadata.put(QUEUE_STATE_KEY, queueState);
		// The message has queue position 0
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		final Message message = new Message(messageId, groupId, timestamp, raw);
		final Metadata messageMetadata = new Metadata();
		context.checking(new Expectations() {{
			oneOf(validationManager).registerIncomingMessageHook(with(clientId),
					with(any(IncomingMessageHook.class)));
			will(new CaptureArgumentAction<IncomingMessageHook>(captured,
					IncomingMessageHook.class, 1));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata));
			// Queue position 0 is expected
			oneOf(clientHelper).toDictionary(queueState, 0, queueState.length);
			will(new DecodeQueueStateAction(0L, 0L, new BdfList()));
			// The message should be delegated
			oneOf(incomingQueueMessageHook).incomingMessage(with(txn),
					with(any(QueueMessage.class)), with(messageMetadata));
			// Queue position 1 should be expected next
			oneOf(clientHelper).toByteArray(with(any(BdfDictionary.class)));
			will(new EncodeQueueStateAction(0L, 1L, new BdfList()));
			oneOf(db).mergeGroupMetadata(with(txn), with(groupId),
					with(any(Metadata.class)));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating incoming message hook
		mqm.registerIncomingMessageHook(clientId, incomingQueueMessageHook);
		IncomingMessageHook delegate = captured.get();
		assertNotNull(delegate);
		// Pass the message to the hook
		delegate.incomingMessage(txn, message, messageMetadata);

		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingMessageHookRetrievesPendingMessage()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ClientHelper clientHelper = context.mock(ClientHelper.class);
		final QueueMessageFactory queueMessageFactory =
				context.mock(QueueMessageFactory.class);
		final ValidationManager validationManager =
				context.mock(ValidationManager.class);
		final AtomicReference<IncomingMessageHook> captured =
				new AtomicReference<IncomingMessageHook>();
		final IncomingQueueMessageHook incomingQueueMessageHook =
				context.mock(IncomingQueueMessageHook.class);
		final Transaction txn = new Transaction(null);
		final Metadata groupMetadata = new Metadata();
		final byte[] queueState = new byte[123];
		groupMetadata.put(QUEUE_STATE_KEY, queueState);
		// The message has queue position 0
		final MessageId messageId = new MessageId(TestUtils.getRandomId());
		final byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		final Message message = new Message(messageId, groupId, timestamp, raw);
		final Metadata messageMetadata = new Metadata();
		// Queue position 1 is pending
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final byte[] raw1 = new byte[QUEUE_MESSAGE_HEADER_LENGTH];
		final QueueMessage message1 = new QueueMessage(messageId1, groupId,
				timestamp, 1L, raw1);
		final Metadata messageMetadata1 = new Metadata();
		final BdfList pending = BdfList.of(BdfList.of(1L, messageId1));
		context.checking(new Expectations() {{
			oneOf(validationManager).registerIncomingMessageHook(with(clientId),
					with(any(IncomingMessageHook.class)));
			will(new CaptureArgumentAction<IncomingMessageHook>(captured,
					IncomingMessageHook.class, 1));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(groupMetadata));
			// Queue position 0 is expected, position 1 is pending
			oneOf(clientHelper).toDictionary(queueState, 0, queueState.length);
			will(new DecodeQueueStateAction(0L, 0L, pending));
			// The message should be delegated
			oneOf(incomingQueueMessageHook).incomingMessage(with(txn),
					with(any(QueueMessage.class)), with(messageMetadata));
			// The pending message should be retrieved
			oneOf(db).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(db).getMessageMetadata(txn, messageId1);
			will(returnValue(messageMetadata1));
			oneOf(queueMessageFactory).createMessage(messageId1, raw1);
			will(returnValue(message1));
			// The pending message should be delegated
			oneOf(incomingQueueMessageHook).incomingMessage(txn, message1,
					messageMetadata1);
			// Queue position 2 should be expected next
			oneOf(clientHelper).toByteArray(with(any(BdfDictionary.class)));
			will(new EncodeQueueStateAction(0L, 2L, new BdfList()));
			oneOf(db).mergeGroupMetadata(with(txn), with(groupId),
					with(any(Metadata.class)));
		}});


		MessageQueueManagerImpl mqm = new MessageQueueManagerImpl(db,
				clientHelper, queueMessageFactory, validationManager);

		// Capture the delegating incoming message hook
		mqm.registerIncomingMessageHook(clientId, incomingQueueMessageHook);
		IncomingMessageHook delegate = captured.get();
		assertNotNull(delegate);
		// Pass the message to the hook
		delegate.incomingMessage(txn, message, messageMetadata);

		context.assertIsSatisfied();
	}

	private class EncodeQueueStateAction implements Action {

		private final long outgoingPosition, incomingPosition;
		private final BdfList pending;

		private EncodeQueueStateAction(long outgoingPosition,
				long incomingPosition, BdfList pending) {
			this.outgoingPosition = outgoingPosition;
			this.incomingPosition = incomingPosition;
			this.pending = pending;
		}

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			BdfDictionary d = (BdfDictionary) invocation.getParameter(0);
			assertEquals(outgoingPosition, d.getLong("nextOut").longValue());
			assertEquals(incomingPosition, d.getLong("nextIn").longValue());
			assertEquals(pending, d.getList("pending"));
			return new byte[123];
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("encodes a queue state");
		}
	}

	private class DecodeQueueStateAction implements Action {

		private final long outgoingPosition, incomingPosition;
		private final BdfList pending;

		private DecodeQueueStateAction(long outgoingPosition,
				long incomingPosition, BdfList pending) {
			this.outgoingPosition = outgoingPosition;
			this.incomingPosition = incomingPosition;
			this.pending = pending;
		}

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			BdfDictionary d = new BdfDictionary();
			d.put("nextOut", outgoingPosition);
			d.put("nextIn", incomingPosition);
			d.put("pending", pending);
			return d;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("decodes a queue state");
		}
	}

	private class CreateMessageAction implements Action {

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			GroupId groupId = (GroupId) invocation.getParameter(0);
			long timestamp = (Long) invocation.getParameter(1);
			long queuePosition = (Long) invocation.getParameter(2);
			byte[] body = (byte[]) invocation.getParameter(3);
			byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH + body.length];
			MessageId id = new MessageId(TestUtils.getRandomId());
			return new QueueMessage(id, groupId, timestamp, queuePosition, raw);
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("creates a message");
		}
	}

	private class CaptureArgumentAction<T> implements Action {

		private final AtomicReference<T> captured;
		private final Class<T> capturedClass;
		private final int index;

		private CaptureArgumentAction(AtomicReference<T> captured,
				Class<T> capturedClass, int index) {
			this.captured = captured;
			this.capturedClass = capturedClass;
			this.index = index;
		}

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			captured.set(capturedClass.cast(invocation.getParameter(index)));
			return null;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("captures an argument");
		}
	}
}
