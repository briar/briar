package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageContext;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.bramble.api.sync.ValidationManager.MessageValidator;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.api.sync.event.MessageAddedEvent;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.ByteUtils;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.bramble.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;

public class ValidationManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);
	private final MessageValidator validator =
			context.mock(MessageValidator.class);
	private final IncomingMessageHook hook =
			context.mock(IncomingMessageHook.class);

	private final Executor dbExecutor = new ImmediateExecutor();
	private final Executor validationExecutor = new ImmediateExecutor();
	private final ClientId clientId =
			new ClientId(TestUtils.getRandomString(5));
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

	private ValidationManagerImpl vm;

	public ValidationManagerImplTest() {
		// Encode the messages
		System.arraycopy(groupId.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
	}

	@Before
	public void setUp() {
		vm = new ValidationManagerImpl(db, dbExecutor, validationExecutor,
				messageFactory);
		vm.registerMessageValidator(clientId, validator);
		vm.registerIncomingMessageHook(clientId, hook);
	}

	@Test
	public void testStartAndStop() throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, true);
		final Transaction txn2 = new Transaction(null, true);

		context.checking(new Expectations() {{
			// validateOutstandingMessages()
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessagesToValidate(txn, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// deliverOutstandingMessages()
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
			// shareOutstandingMessages()
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getMessagesToShare(txn2, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});

		vm.startService();
		vm.stopService();
	}

	@Test
	public void testMessagesAreValidatedAtStartup() throws Exception {
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// Load the first raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(messageFactory).createMessage(messageId, raw);
			will(returnValue(message));
			oneOf(db).getGroup(txn1, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn1);
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
			oneOf(db).setMessageState(txn2, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn3));
			oneOf(db).getRawMessage(txn3, messageId1);
			will(returnValue(raw));
			oneOf(messageFactory).createMessage(messageId1, raw);
			will(returnValue(message1));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn3);
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
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getPendingMessages(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn5);
			oneOf(db).endTransaction(txn5);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn6));
			oneOf(db).getMessagesToShare(txn6, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn6);
			oneOf(db).endTransaction(txn6);
		}});

		vm.startService();
	}

	@Test
	public void testPendingMessagesAreDeliveredAtStartup() throws Exception {
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).commitTransaction(txn1);
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
			oneOf(messageFactory).createMessage(messageId, raw);
			will(returnValue(message));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId);
			will(returnValue(new Metadata()));
			// Deliver the message
			oneOf(hook).incomingMessage(txn2, message, metadata);
			will(returnValue(false));
			oneOf(db).setMessageState(txn2, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn2, messageId);
			will(returnValue(Collections.singletonMap(messageId2, PENDING)));
			oneOf(db).commitTransaction(txn2);
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
			oneOf(messageFactory).createMessage(messageId2, raw);
			will(returnValue(message2));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId2);
			will(returnValue(metadata));
			// Deliver the dependent
			oneOf(hook).incomingMessage(txn3, message2, metadata);
			will(returnValue(false));
			oneOf(db).setMessageState(txn3, messageId2, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn3);
			oneOf(db).endTransaction(txn3);

			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getMessagesToShare(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
		}});

		vm.startService();
	}

	@Test
	public void testMessagesAreSharedAtStartup() throws Exception {
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// No pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getPendingMessages(txn1, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);

			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getMessagesToShare(txn2, clientId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
			// Share message and get dependencies
			oneOf(db).startTransaction(false);
			will(returnValue(txn3));
			oneOf(db).setMessageShared(txn3, messageId);
			oneOf(db).getMessageDependencies(txn3, messageId);
			will(returnValue(Collections.singletonMap(messageId2, DELIVERED)));
			oneOf(db).commitTransaction(txn3);
			oneOf(db).endTransaction(txn3);
			// Share dependency
			oneOf(db).startTransaction(false);
			will(returnValue(txn4));
			oneOf(db).setMessageShared(txn4, messageId2);
			oneOf(db).getMessageDependencies(txn4, messageId2);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
		}});

		vm.startService();
	}

	@Test
	public void testIncomingMessagesAreShared() throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			// Share message
			oneOf(db).setMessageShared(txn1, messageId);
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
			// Share dependencies
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).setMessageShared(txn2, messageId1);
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testValidationContinuesAfterNoSuchMessageException()
			throws Exception {
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// Load the first raw message - *gasp* it's gone!
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(throwException(new NoSuchMessageException()));
			never(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(messageFactory).createMessage(messageId1, raw);
			will(returnValue(message1));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn2);
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
			oneOf(db).commitTransaction(txn3);
			oneOf(db).endTransaction(txn3);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getPendingMessages(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getMessagesToShare(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn5);
			oneOf(db).endTransaction(txn5);
		}});

		vm.startService();
	}

	@Test
	public void testValidationContinuesAfterNoSuchGroupException()
			throws Exception {
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
			// Load the first raw message
			oneOf(db).startTransaction(true);
			will(returnValue(txn1));
			oneOf(db).getRawMessage(txn1, messageId);
			will(returnValue(raw));
			oneOf(messageFactory).createMessage(messageId, raw);
			will(returnValue(message));
			// Load the group - *gasp* it's gone!
			oneOf(db).getGroup(txn1, groupId);
			will(throwException(new NoSuchGroupException()));
			never(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
			// Load the second raw message and group
			oneOf(db).startTransaction(true);
			will(returnValue(txn2));
			oneOf(db).getRawMessage(txn2, messageId1);
			will(returnValue(raw));
			oneOf(messageFactory).createMessage(messageId1, raw);
			will(returnValue(message1));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn2);
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
			oneOf(db).commitTransaction(txn3);
			oneOf(db).endTransaction(txn3);
			// Get pending messages to deliver
			oneOf(db).startTransaction(true);
			will(returnValue(txn4));
			oneOf(db).getPendingMessages(txn4, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
			// Get messages to share
			oneOf(db).startTransaction(true);
			will(returnValue(txn5));
			oneOf(db).getMessagesToShare(txn5, clientId);
			will(returnValue(Collections.emptyList()));
			oneOf(db).commitTransaction(txn5);
			oneOf(db).endTransaction(txn5);
		}});

		vm.startService();
	}

	@Test
	public void testNonLocalMessagesAreValidatedWhenAdded() throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testLocalMessagesAreNotValidatedWhenAdded() throws Exception {
		vm.eventOccurred(new MessageAddedEvent(message, null));
	}

	@Test
	public void testMessagesWithUndeliveredDependenciesArePending()
			throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testMessagesWithDeliveredDependenciesGetDelivered()
			throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testMessagesWithInvalidDependenciesAreInvalid()
			throws Exception {
		final Transaction txn = new Transaction(null, true);
		final Transaction txn1 = new Transaction(null, false);
		final Transaction txn2 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Load the group
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).commitTransaction(txn1);
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
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testRecursiveInvalidation() throws Exception {
		final MessageId messageId3 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId4 = new MessageId(TestUtils.getRandomId());
		final Map<MessageId, State> twoDependents =
				new LinkedHashMap<MessageId, State>();
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
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).commitTransaction(txn1);
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
			oneOf(db).commitTransaction(txn2);
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
			oneOf(db).commitTransaction(txn3);
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
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
			// Invalidate message 3 (again, via 2)
			oneOf(db).startTransaction(false);
			will(returnValue(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(INVALID)); // Already invalidated
			oneOf(db).commitTransaction(txn5);
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
			oneOf(db).commitTransaction(txn6);
			oneOf(db).endTransaction(txn6);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testPendingDependentsGetDelivered() throws Exception {
		final MessageId messageId3 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId4 = new MessageId(TestUtils.getRandomId());
		final Message message3 = new Message(messageId3, groupId, timestamp,
				raw);
		final Message message4 = new Message(messageId4, groupId, timestamp,
				raw);
		final Map<MessageId, State> twoDependents =
				new LinkedHashMap<MessageId, State>();
		twoDependents.put(messageId1, PENDING);
		twoDependents.put(messageId2, PENDING);
		final Map<MessageId, State> twoDependencies =
				new LinkedHashMap<MessageId, State>();
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
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// The message has two pending dependents: 1 and 2
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(twoDependents));
			oneOf(db).commitTransaction(txn1);
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
			oneOf(messageFactory).createMessage(messageId1, raw);
			will(returnValue(message1));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId1);
			will(returnValue(metadata));
			// Deliver message 1
			oneOf(hook).incomingMessage(txn2, message1, metadata);
			will(returnValue(false));
			oneOf(db).setMessageState(txn2, messageId1, DELIVERED);
			// Message 1 has one pending dependent: 3
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).commitTransaction(txn2);
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
			oneOf(messageFactory).createMessage(messageId2, raw);
			will(returnValue(message2));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId2);
			will(returnValue(metadata));
			// Deliver message 2
			oneOf(hook).incomingMessage(txn3, message2, metadata);
			will(returnValue(false));
			oneOf(db).setMessageState(txn3, messageId2, DELIVERED);
			// Message 2 has one pending dependent: 3 (same dependent as 1)
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(Collections.singletonMap(messageId3, PENDING)));
			oneOf(db).commitTransaction(txn3);
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
			oneOf(messageFactory).createMessage(messageId3, raw);
			will(returnValue(message3));
			oneOf(db).getGroup(txn4, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn4, messageId3);
			will(returnValue(metadata));
			// Deliver message 3
			oneOf(hook).incomingMessage(txn4, message3, metadata);
			oneOf(db).setMessageState(txn4, messageId3, DELIVERED);
			// Message 3 has one pending dependent: 4
			oneOf(db).getMessageDependents(txn4, messageId3);
			will(returnValue(Collections.singletonMap(messageId4, PENDING)));
			oneOf(db).commitTransaction(txn4);
			oneOf(db).endTransaction(txn4);
			// Check whether message 3 is ready to be delivered (again, via 2)
			oneOf(db).startTransaction(false);
			will(returnValue(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(DELIVERED)); // Already delivered
			oneOf(db).commitTransaction(txn5);
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
			oneOf(messageFactory).createMessage(messageId4, raw);
			will(returnValue(message4));
			oneOf(db).getGroup(txn6, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn6, messageId4);
			will(returnValue(metadata));
			// Deliver message 4
			oneOf(hook).incomingMessage(txn6, message4, metadata);
			will(returnValue(false));
			oneOf(db).setMessageState(txn6, messageId4, DELIVERED);
			// Message 4 has no pending dependents
			oneOf(db).getMessageDependents(txn6, messageId4);
			will(returnValue(Collections.emptyMap()));
			oneOf(db).commitTransaction(txn6);
			oneOf(db).endTransaction(txn6);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testOnlyReadyPendingDependentsGetDelivered() throws Exception {
		final Map<MessageId, State> twoDependencies =
				new LinkedHashMap<MessageId, State>();
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
			oneOf(db).commitTransaction(txn);
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
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(Collections.singletonMap(messageId1, PENDING)));
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
			// Check whether the pending dependent is ready to be delivered
			oneOf(db).startTransaction(false);
			will(returnValue(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(twoDependencies));
			oneOf(db).commitTransaction(txn2);
			oneOf(db).endTransaction(txn2);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}
}
