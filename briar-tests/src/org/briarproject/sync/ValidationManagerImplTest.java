package org.briarproject.sync;

import org.briarproject.BriarTestCase;
import org.briarproject.ImmediateExecutor;
import org.briarproject.TestUtils;
import org.briarproject.api.UniqueId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.api.sync.ValidationManager.MessageValidator;
import org.briarproject.util.ByteUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Executor;

public class ValidationManagerImplTest extends BriarTestCase {

	private final ClientId clientId = new ClientId(TestUtils.getRandomId());
	private final MessageId messageId = new MessageId(TestUtils.getRandomId());
	private final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
	private final GroupId groupId = new GroupId(TestUtils.getRandomId());
	private final byte[] descriptor = new byte[32];
	private final Group group = new Group(groupId, clientId, descriptor);
	private final long timestamp = System.currentTimeMillis();
	private final byte[] raw = new byte[123];
	private final Message message = new Message(messageId, groupId, timestamp,
			raw);
	private final Message message1 = new Message(messageId1, groupId, timestamp,
			raw);
	private final Metadata metadata = new Metadata();
	private final ContactId contactId = new ContactId(234);

	public ValidationManagerImplTest() {
		// Encode the messages
		System.arraycopy(groupId.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
	}

	@Test
	public void testMessagesAreValidatedAtStartup() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, false);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, false);
		final Transaction txn4 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Arrays.asList(messageId, messageId1)));
			oneOf(db).endTransaction(txn);
			// Load the first raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).getGroup(txn1, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn1);
			// Validate the first message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(metadata));
			// Store the validation result for the first message
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).mergeMessageMetadata(txn2, messageId, metadata);
			oneOf(db).setMessageValid(txn2, message, clientId, true);
			oneOf(db).setMessageShared(txn2, message, true);
			// Call the hook for the first message
			oneOf(hook).incomingMessage(txn2, message, metadata);
			oneOf(db).endTransaction(txn2);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn3));
			oneOf(db).getRawMessage(txn3, messageId1);
			will(returnValue(raw));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn3);
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(returnValue(null));
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).setMessageValid(txn4, message1, clientId, false);
			oneOf(db).endTransaction(txn4);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.start();

		context.assertIsSatisfied();
	}

	@Test
	public void testValidationContinuesAfterNoSuchMessageException()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, true);
		final Transaction txn2 = new Transaction(null, true);
		final Transaction txn3 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Arrays.asList(messageId, messageId1)));
			oneOf(db).endTransaction(txn);
			// Load the first raw message - *gasp* it's gone!
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(throwException(new NoSuchMessageException()));
			oneOf(db).endTransaction(txn1);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn2);
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(returnValue(null));
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageValid(txn3, message1, clientId, false);
			oneOf(db).endTransaction(txn3);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.start();

		context.assertIsSatisfied();
	}

	@Test
	public void testValidationContinuesAfterNoSuchGroupException()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, true);
		final Transaction txn2 = new Transaction(null, true);
		final Transaction txn3 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Arrays.asList(messageId, messageId1)));
			oneOf(db).endTransaction(txn);
			// Load the first raw message
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			// Load the group - *gasp* it's gone!
			oneOf(db).getGroup(txn1, groupId);
			will(throwException(new NoSuchGroupException()));
			oneOf(db).endTransaction(txn1);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn2);
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(returnValue(null));
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageValid(txn3, message1, clientId, false);
			oneOf(db).endTransaction(txn3);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.start();

		context.assertIsSatisfied();
	}

	@Test
	public void testNonLocalMessagesAreValidatedWhenAdded() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(metadata));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageValid(txn1, message, clientId, true);
			oneOf(db).setMessageShared(txn1, message, true);
			// Call the hook
			oneOf(hook).incomingMessage(txn1, message, metadata);
			oneOf(db).endTransaction(txn1);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testLocalMessagesAreNotValidatedWhenAdded() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, null));

		context.assertIsSatisfied();
	}
}
