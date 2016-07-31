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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.api.sync.ValidationManager.State.VALID;
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
	private final Collection<MessageId> dependencies = new ArrayList<>();
	private final MessageContext validResultWithDependencies =
			new MessageContext(metadata, dependencies);
	private final Map<MessageId, State> states = new HashMap<>();

	public ValidationManagerImplTest() {
		// Encode the messages
		System.arraycopy(groupId.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);

		dependencies.add(messageId1);
		states.put(messageId1, INVALID);
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
		final Transaction txn2b = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, false);
		final Transaction txn4 = new Transaction(null, false);
		final Transaction txn5 = new Transaction(null, true);
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
			oneOf(db).getMessageDependencies(txn2, messageId);
			oneOf(db).mergeMessageMetadata(txn2, messageId, metadata);
			oneOf(db).setMessageState(txn2, message, clientId, VALID);
			oneOf(db).endTransaction(txn2);
			// Async delivery
			oneOf(db).startTransaction(false);
			will(returnValue(txn2b));
			oneOf(db).setMessageShared(txn2b, message, true);
			// Call the hook for the first message
			oneOf(hook).incomingMessage(txn2b, message, metadata);
			oneOf(db).getRawMessage(txn2b, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2b, message, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn2b, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn2b);
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
			oneOf(db).setMessageState(txn4, message1, clientId, INVALID);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn4, messageId1);
			oneOf(db).deleteMessage(txn4, messageId1);
			oneOf(db).deleteMessageMetadata(txn4, messageId1);
			oneOf(db).endTransaction(txn4);
			// Get other messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getMessagesToDeliver(txn5, clientId);
			oneOf(db).getPendingMessages(txn5, clientId);
			oneOf(db).endTransaction(txn5);
		}});

		ValidationManagerImpl vm = new ValidationManagerImpl(db, dbExecutor,
				cryptoExecutor);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
		vm.startService();

		context.assertIsSatisfied();
	}

	@Test
	public void testMessagesAreDeliveredAtStartup() throws Exception {
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
		final Transaction txn5 = new Transaction(null, false);

		states.put(messageId1, PENDING);

		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			oneOf(db).endTransaction(txn);
			// Get IDs of messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getMessagesToDeliver(txn1, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).getPendingMessages(txn1, clientId);
			oneOf(db).endTransaction(txn1);
			// Get message and its metadata to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(message.getRaw()));
			oneOf(db).getGroup(txn2, message.getGroupId());
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId);
			will(returnValue(metadata));
			oneOf(db).endTransaction(txn2);
			// Deliver message in a new transaction
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageShared(txn3, message, true);
			oneOf(db).setMessageState(txn3, message, clientId, DELIVERED);
			oneOf(hook).incomingMessage(txn3, message, metadata);
			oneOf(db).getRawMessage(txn3, messageId);
			will(returnValue(message.getRaw()));
			// Try to also deliver pending dependents
			oneOf(db).getMessageDependents(txn3, messageId);
			will(returnValue(states));
			oneOf(db).getMessageDependencies(txn3, messageId1);
			will(returnValue(Collections.singletonMap(messageId2, DELIVERED)));
			oneOf(db).endTransaction(txn3);
			// Get the dependent to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getRawMessage(txn4, messageId1);
			will(returnValue(message1.getRaw()));
			oneOf(db).getGroup(txn4, message.getGroupId());
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn4, messageId1);
			will(returnValue(metadata));
			oneOf(db).endTransaction(txn4);
			// Deliver the dependent in a new transaction
			oneOf(db).startTransaction(false);
			will(returnValue(txn5));
			oneOf(db).setMessageShared(txn5, message1, true);
			oneOf(db).setMessageState(txn5, message1, clientId, DELIVERED);
			oneOf(hook).incomingMessage(txn5, message1, metadata);
			oneOf(db).getRawMessage(txn5, messageId1);
			will(returnValue(message1.getRaw()));
			oneOf(db).getMessageDependents(txn5, messageId1);
			oneOf(db).endTransaction(txn5);
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
		final Transaction txn2 = new Transaction(null, true);
		final Transaction txn3 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get messages to validate
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			oneOf(db).endTransaction(txn);
			// Get IDs of messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getMessagesToDeliver(txn1, clientId);
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).endTransaction(txn1);
			// Get message and its metadata to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(message.getRaw()));
			oneOf(db).getGroup(txn2, message.getGroupId());
			will(returnValue(group));
			oneOf(db).getMessageDependencies(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId1, DELIVERED)));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId);
			oneOf(db).endTransaction(txn2);
			// Deliver the pending message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageShared(txn3, message, true);
			oneOf(db).setMessageState(txn3, message, clientId, DELIVERED);
			oneOf(hook).incomingMessage(txn3, message, metadata);
			oneOf(db).getRawMessage(txn3, messageId);
			will(returnValue(message.getRaw()));
			oneOf(db).getMessageDependents(txn3, messageId);
			oneOf(db).endTransaction(txn3);
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
			// Store the validation result for the second message
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageState(txn3, message1, clientId, INVALID);
			// recursively invalidate dependents
			oneOf(db).getMessageDependents(txn3, messageId1);
			oneOf(db).deleteMessage(txn3, messageId1);
			oneOf(db).deleteMessageMetadata(txn3, messageId1);
			oneOf(db).endTransaction(txn3);
			// Get other messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getMessagesToDeliver(txn4, clientId);
			oneOf(db).getPendingMessages(txn4, clientId);
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
			oneOf(db).setMessageState(txn3, message1, clientId, INVALID);
			// recursively invalidate dependents
			oneOf(db).getMessageDependents(txn3, messageId1);
			oneOf(db).deleteMessage(txn3, messageId1);
			oneOf(db).deleteMessageMetadata(txn3, messageId1);
			oneOf(db).endTransaction(txn3);
			// Get other messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getMessagesToDeliver(txn4, clientId);
			oneOf(db).getPendingMessages(txn4, clientId);
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
			oneOf(db).getMessageDependencies(txn1, messageId);
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, VALID);
			oneOf(db).endTransaction(txn1);
			// async delivery
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, message, true);
			// Call the hook
			oneOf(hook).incomingMessage(txn2, message, metadata);
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, message, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn2, messageId);
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
	public void testMessagesWithNonDeliveredDependenciesArePending()
			throws Exception {

		states.put(messageId1, UNKNOWN);
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
			will(returnValue(states));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, PENDING);
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

		states.put(messageId1, DELIVERED);
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
			will(returnValue(states));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, VALID);
			oneOf(db).endTransaction(txn1);
			// async delivery
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, message, true);
			// Call the hook
			oneOf(hook).incomingMessage(txn2, message, metadata);
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, message, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn2, messageId);
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
		final Transaction txn3 = new Transaction(null, true);
		final Transaction txn4 = new Transaction(null, false);
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
			will(returnValue(states));
			oneOf(db).endTransaction(txn1);
			// Invalidate message in a new transaction
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId2, UNKNOWN)));
			oneOf(db).setMessageState(txn2, message, clientId, INVALID);
			oneOf(db).deleteMessage(txn2, messageId);
			oneOf(db).deleteMessageMetadata(txn2, messageId);
			oneOf(db).endTransaction(txn2);
			// Get message to invalidate in a new transaction
			oneOf(db).startTransaction(true);
			will(returnValue(txn3));
			oneOf(db).getRawMessage(txn3, messageId2);
			will(returnValue(message2.getRaw()));
			oneOf(db).getGroup(txn3, message2.getGroupId());
			will(returnValue(group));
			oneOf(db).endTransaction(txn3);
			// Invalidate dependent message in a new transaction
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).getMessageDependents(txn4, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).setMessageState(txn4, message2, clientId, INVALID);
			oneOf(db).deleteMessage(txn4, messageId2);
			oneOf(db).deleteMessageMetadata(txn4, messageId2);
			oneOf(db).endTransaction(txn4);
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
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);
		final Transaction txn3 = new Transaction(null, true);
		final Transaction txn4 = new Transaction(null, false);
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
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, VALID);
			oneOf(db).endTransaction(txn1);
			// Deliver first message
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, message, true);
			oneOf(hook).incomingMessage(txn2, message, metadata);
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, message, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId1, PENDING)));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId2, DELIVERED)));
			oneOf(db).endTransaction(txn2);
			// Also get the pending message for delivery
			oneOf(db).startTransaction(true);
			will(returnValue(txn3));
			oneOf(db).getRawMessage(txn3, messageId1);
			will(returnValue(message1.getRaw()));
			oneOf(db).getGroup(txn3, message1.getGroupId());
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId1);
			will(returnValue(metadata));
			oneOf(db).endTransaction(txn3);
			// Deliver the pending message
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).setMessageShared(txn4, message1, true);
			oneOf(hook).incomingMessage(txn4, message1, metadata);
			oneOf(db).getRawMessage(txn4, messageId1);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn4, message1, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn4, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).endTransaction(txn4);
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
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, VALID);
			oneOf(db).endTransaction(txn1);
			// Deliver first message
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, message, true);
			oneOf(hook).incomingMessage(txn2, message, metadata);
			oneOf(db).getRawMessage(txn2, messageId);
			will(returnValue(raw));
			oneOf(db).setMessageState(txn2, message, clientId, DELIVERED);
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId1, PENDING)));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId2, VALID)));
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
		states.put(messageId1, UNKNOWN);
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
			will(returnValue(states));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, message, clientId, PENDING);
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
			oneOf(db).setMessageState(txn3, message1, clientId, PENDING);
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
