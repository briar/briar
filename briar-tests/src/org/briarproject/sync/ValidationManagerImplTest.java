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
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageContext;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.api.sync.ValidationManager.MessageValidator;
import org.briarproject.api.sync.ValidationManager.State;
import org.briarproject.util.ByteUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.api.sync.ValidationManager.State.UNKNOWN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ValidationManagerImplTest extends BriarTestCase {

	private final ClientId clientId = new ClientId(TestUtils.getRandomId());
	private final MessageId messageId = new MessageId(TestUtils.getRandomId());
	private final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
	private final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
	private final GroupId groupId = new GroupId(TestUtils.getRandomId());
	private final byte[] descriptor = new byte[32];
	private final Group group = new Group(groupId, clientId, descriptor);
	private final long timestamp = System.currentTimeMillis();
	private final byte[] raw = new byte[123];
	private final Message message = new Message(messageId, groupId, timestamp,
			raw);
	private final Message message1 = new Message(messageId1, groupId, timestamp,
			raw);
	private final Message message2 = new Message(messageId2, groupId, timestamp,
			raw);

	private final Metadata metadata = new Metadata();
	private final MessageContext validResult = new MessageContext(metadata);
	private final ContactId contactId = new ContactId(234);
	private final MessageContext validResultWithDependencies =
			new MessageContext(metadata, Collections.singletonList(messageId1));

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
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, true);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, true);
		final Transaction txn4 = new Transaction(null, false);
		final Transaction txn5 = new Transaction(null, true);
		final Transaction txn6 = new Transaction(null, true);
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
			will(returnValue(validResult));
			// Store the validation result for the first message
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).mergeMessageMetadata(txn2, messageId, metadata);
			// Deliver the first message
			oneOf(hook).incomingMessage(txn2, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.emptyMap()));
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
			will(throwException(new InvalidMessageException()));
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).getMessageState(txn4, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn4, messageId1, INVALID);
			oneOf(db).deleteMessage(txn4, messageId1);
			oneOf(db).deleteMessageMetadata(txn4, messageId1);
			// Recursively invalidate any dependents
			oneOf(db).getMessageDependents(txn4, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn4);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getPendingMessages(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn5);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn6));
			oneOf(db).getMessagesToShare(txn6, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn6);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
		assertTrue(txn5.isComplete());
		assertTrue(txn6.isComplete());
	}

	@Test
	public void testPendingMessagesAreDeliveredAtStartup() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, true);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, false);
		final Transaction txn4 = new Transaction(null, true);

		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).endTransaction(txn1);
			// Check whether the message is ready to deliver
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId1, DELIVERED)));
			// Get the message and its metadata to deliver
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId);
			will(returnValue(new Metadata()));
			// Deliver the message
			oneOf(hook).incomingMessage(txn2, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId2, PENDING)));
			oneOf(db).endTransaction(txn2);
			// Check whether the dependent is ready to deliver
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).getMessageState(txn3, messageId2);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn3, messageId2);
			will(returnValue(Collections.singletonMap(messageId1, DELIVERED)));
			// Get the dependent and its metadata to deliver
			oneOf(db).getRawMessage(txn3, messageId2);
			will(returnValue(raw));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId2);
			will(returnValue(metadata));
			// Deliver the dependent
			oneOf(hook).incomingMessage(txn3, message2, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn3, messageId2);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn3, messageId2, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn3);

			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getMessagesToShare(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn4);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
	}

	@Test
	public void testMessagesAreSharedAtStartup() throws Exception {
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
		final Transaction txn4 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// No messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn);
			// No pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn1);

			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getMessagesToShare(txn2, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).endTransaction(txn2);
			// Share message and get dependencies
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageShared(txn3, messageId);
			oneOf(db).getMessageDependencies(txn3, messageId);
			will(returnValue(Collections.singletonMap(messageId2, DELIVERED)));
			oneOf(db).endTransaction(txn3);
			// Share dependency
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).setMessageShared(txn4, messageId2);
			oneOf(db).getMessageDependencies(txn4, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn4);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
	}

	@Test
	public void testIncomingMessagesAreShared() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, DELIVERED)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(true));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			// Share message
			oneOf(db).setMessageShared(txn1, messageId);
			oneOf(db).endTransaction(txn1);
			// Share dependencies
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, messageId1);
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn2);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
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
		final Transaction txn4 = new Transaction(null, true);
		final Transaction txn5 = new Transaction(null, true);
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
			will(throwException(new InvalidMessageException()));
			// Invalidate the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).getMessageState(txn3, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn3, messageId1, INVALID);
			oneOf(db).deleteMessage(txn3, messageId1);
			oneOf(db).deleteMessageMetadata(txn3, messageId1);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn3, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn3);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getPendingMessages(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn4);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getMessagesToShare(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn5);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertFalse(txn1.isComplete()); // Aborted due to NoSuchMessageException
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
		assertTrue(txn5.isComplete());
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
		final Transaction txn4 = new Transaction(null, true);
		final Transaction txn5 = new Transaction(null, true);
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
			will(throwException(new InvalidMessageException()));
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).getMessageState(txn3, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn3, messageId1, INVALID);
			oneOf(db).deleteMessage(txn3, messageId1);
			oneOf(db).deleteMessageMetadata(txn3, messageId1);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn3, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn3);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getPendingMessages(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn4);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getMessagesToShare(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).endTransaction(txn5);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertFalse(txn1.isComplete()); // Aborted due to NoSuchGroupException
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
		assertTrue(txn5.isComplete());
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
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn1);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
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

	@Test
	public void testMessagesWithUndeliveredDependenciesArePending()
			throws Exception {

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
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, UNKNOWN)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, messageId, PENDING);
			oneOf(db).endTransaction(txn1);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
	}

	@Test
	public void testMessagesWithDeliveredDependenciesGetDelivered()
			throws Exception {
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
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, DELIVERED)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn1);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
	}

	@Test
	public void testMessagesWithInvalidDependenciesAreInvalid()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			// Check for invalid dependencies
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, INVALID)));
			// Invalidate message
			oneOf(db).getMessageState(txn1, messageId);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn1, messageId, INVALID);
			oneOf(db).deleteMessage(txn1, messageId);
			oneOf(db).deleteMessageMetadata(txn1, messageId);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId2, UNKNOWN)));
			oneOf(db).endTransaction(txn1);
			// Invalidate dependent in a new transaction
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId2);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn2, messageId2, INVALID);
			oneOf(db).deleteMessage(txn2, messageId2);
			oneOf(db).deleteMessageMetadata(txn2, messageId2);
			oneOf(db).getMessageDependents(txn2, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn2);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
	}

	@Test
	public void testRecursiveInvalidation() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final MessageId messageId3 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId4 = new MessageId(TestUtils.getRandomId());
		final Map<MessageId, State> twoDependents = new LinkedHashMap<>();
		twoDependents.put(messageId1, PENDING);
		twoDependents.put(messageId2, PENDING);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, false);
		final Transaction txn4 = new Transaction(null, false);
		final Transaction txn5 = new Transaction(null, false);
		final Transaction txn6 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: invalid
			oneOf(validator).validateMessage(message, group);
			will(throwException(new InvalidMessageException()));
			// Invalidate the message
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).getMessageState(txn1, messageId);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn1, messageId, INVALID);
			oneOf(db).deleteMessage(txn1, messageId);
			oneOf(db).deleteMessageMetadata(txn1, messageId);
			// The message has two dependents: 1 and 2
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(twoDependents));
			oneOf(db).endTransaction(txn1);
			// Invalidate message 1
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn2, messageId1, INVALID);
			oneOf(db).deleteMessage(txn2, messageId1);
			oneOf(db).deleteMessageMetadata(txn2, messageId1);
			// Message 1 has one dependent: 3
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).endTransaction(txn2);
			// Invalidate message 2
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).getMessageState(txn3, messageId2);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn3, messageId2, INVALID);
			oneOf(db).deleteMessage(txn3, messageId2);
			oneOf(db).deleteMessageMetadata(txn3, messageId2);
			// Message 2 has one dependent: 3 (same dependent as 1)
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).endTransaction(txn3);
			// Invalidate message 3 (via 1)
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).getMessageState(txn4, messageId3);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn4, messageId3, INVALID);
			oneOf(db).deleteMessage(txn4, messageId3);
			oneOf(db).deleteMessageMetadata(txn4, messageId3);
			// Message 3 has one dependent: 4
			oneOf(db).getMessageDependents(txn4, messageId3);
			will(returnValue(Collections.singletonMap(messageId4, PENDING)));
			oneOf(db).endTransaction(txn4);
			// Invalidate message 3 (again, via 2)
			oneOf(db).startTransaction(false);
			will(returnValue(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(INVALID)); // Already invalidated
			oneOf(db).endTransaction(txn5);
			// Invalidate message 4 (via 1 and 3)
			oneOf(db).startTransaction(false);
			will(returnValue(txn6));
			oneOf(db).getMessageState(txn6, messageId4);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn6, messageId4, INVALID);
			oneOf(db).deleteMessage(txn6, messageId4);
			oneOf(db).deleteMessageMetadata(txn6, messageId4);
			// Message 4 has no dependents
			oneOf(db).getMessageDependents(txn6, messageId4);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn6);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
	}

	@Test
	public void testPendingDependentsGetDelivered() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final MessageId messageId3 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId4 = new MessageId(TestUtils.getRandomId());
		final Message message3 = new Message(messageId3, groupId, timestamp,
				raw);
		final Message message4 = new Message(messageId4, groupId, timestamp,
				raw);
		final Map<MessageId, State> twoDependents = new LinkedHashMap<>();
		twoDependents.put(messageId1, PENDING);
		twoDependents.put(messageId2, PENDING);
		final Map<MessageId, State> twoDependencies = new LinkedHashMap<>();
		twoDependencies.put(messageId1, DELIVERED);
		twoDependencies.put(messageId2, DELIVERED);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, false);
		final Transaction txn4 = new Transaction(null, false);
		final Transaction txn5 = new Transaction(null, false);
		final Transaction txn6 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// The message has two pending dependents: 1 and 2
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(twoDependents));
			oneOf(db).endTransaction(txn1);
			// Check whether message 1 is ready to be delivered
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId, DELIVERED)));
			// Get message 1 and its metadata
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId1);
			will(returnValue(metadata));
			// Deliver message 1
			oneOf(hook).incomingMessage(txn2, message1, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, messageId1, DELIVERED);
			// Message 1 has one pending dependent: 3
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).endTransaction(txn2);
			// Check whether message 2 is ready to be delivered
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).getMessageState(txn3, messageId2);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn3, messageId2);
			will(returnValue(Collections.singletonMap(messageId, DELIVERED)));
			// Get message 2 and its metadata
			oneOf(db).getRawMessage(txn3, messageId2);
			will(returnValue(raw));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId2);
			will(returnValue(metadata));
			// Deliver message 2
			oneOf(hook).incomingMessage(txn3, message2, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn3, messageId2);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn3, messageId2, DELIVERED);
			// Message 2 has one pending dependent: 3 (same dependent as 1)
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).endTransaction(txn3);
			// Check whether message 3 is ready to be delivered (via 1)
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).getMessageState(txn4, messageId3);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn4, messageId3);
			will(returnValue(twoDependencies));
			// Get message 3 and its metadata
			oneOf(db).getRawMessage(txn4, messageId3);
			will(returnValue(raw));
			oneOf(db).getGroup(txn4, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn4, messageId3);
			will(returnValue(metadata));
			// Deliver message 3
			oneOf(hook).incomingMessage(txn4, message3, metadata);
			oneOf(db).getRawMessage(txn4, messageId3);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn4, messageId3, DELIVERED);
			// Message 3 has one pending dependent: 4
			oneOf(db).getMessageDependents(txn4, messageId3);
			will(returnValue(Collections.singletonMap(messageId4, PENDING)));
			oneOf(db).endTransaction(txn4);
			// Check whether message 3 is ready to be delivered (again, via 2)
			oneOf(db).startTransaction(false);
			will(returnValue(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(DELIVERED)); // Already delivered
			oneOf(db).endTransaction(txn5);
			// Check whether message 4 is ready to be delivered (via 1 and 3)
			oneOf(db).startTransaction(false);
			will(returnValue(txn6));
			oneOf(db).getMessageState(txn6, messageId4);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn6, messageId4);
			will(returnValue(Collections.singletonMap(messageId3, DELIVERED)));
			// Get message 4 and its metadata
			oneOf(db).getRawMessage(txn6, messageId4);
			will(returnValue(raw));
			oneOf(db).getGroup(txn6, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn6, messageId4);
			will(returnValue(metadata));
			// Deliver message 4
			oneOf(hook).incomingMessage(txn6, message4, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn6, messageId4);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn6, messageId4, DELIVERED);
			// Message 4 has no pending dependents
			oneOf(db).getMessageDependents(txn6, messageId4);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn6);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
		assertTrue(txn3.isComplete());
		assertTrue(txn4.isComplete());
		assertTrue(txn5.isComplete());
		assertTrue(txn6.isComplete());
	}

	@Test
	public void testOnlyReadyPendingDependentsGetDelivered() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Map<MessageId, State> twoDependencies = new LinkedHashMap<>();
		twoDependencies.put(messageId, DELIVERED);
		twoDependencies.put(messageId2, UNKNOWN);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(false));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, PENDING)));
			oneOf(db).endTransaction(txn1);
			// Check whether the pending dependent is ready to be delivered
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(twoDependencies));
			oneOf(db).endTransaction(txn2);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
		assertTrue(txn2.isComplete());
	}

	@Test
	public void testMessageDependencyCycle() throws Exception {
		final MessageContext cycleContext = new MessageContext(metadata,
				Collections.singletonList(messageId));

		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Executor dbExecutor = new ImmediateExecutor();
		final Executor cryptoExecutor = new ImmediateExecutor();
		final MessageValidator validator = context.mock(MessageValidator.class);
		final IncomingMessageHook hook =
				context.mock(IncomingMessageHook.class);
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, true);
		final Transaction txn3 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn);
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, UNKNOWN)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, messageId, PENDING);
			oneOf(db).endTransaction(txn1);
			// Second message is coming in
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).endTransaction(txn2);
			// Validate the message: valid
			oneOf(validator).validateMessage(message1, group);
			will(returnValue(cycleContext));
			// Store the validation result
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).addMessageDependencies(txn3, message1,
					cycleContext.getDependencies());
			oneOf(db).getMessageDependencies(txn3, messageId1);
			will(returnValue(Collections.singletonMap(messageId, PENDING)));
			oneOf(db).mergeMessageMetadata(txn3, messageId1, metadata);
			oneOf(db).setMessageState(txn3, messageId1, PENDING);
			oneOf(db).endTransaction(txn3);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.eventOccurred(new MessageAddedEvent(message, contactId));
		vm.eventOccurred(new MessageAddedEvent(message1, contactId));

		context.assertIsSatisfied();

		assertTrue(txn.isComplete());
		assertTrue(txn1.isComplete());
	}

}
