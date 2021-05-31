package org.briarproject.bramble.sync.validation;

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
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.event.MessageAddedEvent;
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;
import org.briarproject.bramble.api.sync.validation.MessageState;
import org.briarproject.bramble.api.sync.validation.MessageValidator;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_DO_NOT_SHARE;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_SHARE;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.sync.validation.MessageState.INVALID;
import static org.briarproject.bramble.api.sync.validation.MessageState.PENDING;
import static org.briarproject.bramble.api.sync.validation.MessageState.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;

public class ValidationManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final MessageValidator validator =
			context.mock(MessageValidator.class);
	private final IncomingMessageHook hook =
			context.mock(IncomingMessageHook.class);

	private final Executor dbExecutor = new ImmediateExecutor();
	private final Executor validationExecutor = new ImmediateExecutor();
	private final ClientId clientId = getClientId();
	private final int majorVersion = 123;
	private final Group group = getGroup(clientId, majorVersion);
	private final GroupId groupId = group.getId();
	private final Message message = getMessage(groupId);
	private final Message message1 = getMessage(groupId);
	private final Message message2 = getMessage(groupId);
	private final MessageId messageId = message.getId();
	private final MessageId messageId1 = message1.getId();
	private final MessageId messageId2 = message2.getId();

	private final Metadata metadata = new Metadata();
	private final MessageContext validResult = new MessageContext(metadata);
	private final ContactId contactId = getContactId();
	private final MessageContext validResultWithDependencies =
			new MessageContext(metadata, singletonList(messageId1));

	private ValidationManagerImpl vm;

	@Before
	public void setUp() {
		vm = new ValidationManagerImpl(db, dbExecutor, validationExecutor);
		vm.registerMessageValidator(clientId, majorVersion, validator);
		vm.registerIncomingMessageHook(clientId, majorVersion, hook);
	}

	@Test
	public void testStartAndStop() throws Exception {
		expectGetMessagesToValidate();
		expectGetPendingMessages();
		expectGetMessagesToShare();

		vm.startService();
		vm.stopService();
	}

	@Test
	public void testMessagesAreValidatedAtStartup() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, true);
		Transaction txn3 = new Transaction(null, false);

		expectGetMessagesToValidate(messageId, messageId1);

		context.checking(new DbExpectations() {{
			// Load the first raw message and group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessage(txn, messageId);
			will(returnValue(message));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the first message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result for the first message
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the first message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(emptyMap()));
			// Load the second raw message and group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn2));
			oneOf(db).getMessage(txn2, messageId1);
			will(returnValue(message1));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(throwException(new InvalidMessageException()));
			// Store the validation result for the second message
			oneOf(db).transaction(with(false), withDbRunnable(txn3));
			oneOf(db).getMessageState(txn3, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn3, messageId1, INVALID);
			oneOf(db).deleteMessage(txn3, messageId1);
			oneOf(db).deleteMessageMetadata(txn3, messageId1);
			// Recursively invalidate any dependents
			oneOf(db).getMessageDependents(txn3, messageId1);
			will(returnValue(emptyMap()));
		}});

		expectGetPendingMessages();
		expectGetMessagesToShare();

		vm.startService();
	}

	@Test
	public void testPendingMessagesAreDeliveredAtStartup() throws Exception {
		Transaction txn = new Transaction(null, false);
		Transaction txn1 = new Transaction(null, false);

		expectGetMessagesToValidate();
		expectGetPendingMessages(messageId);

		context.checking(new DbExpectations() {{
			// Check whether the message is ready to deliver
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).getMessageState(txn, messageId);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn, messageId);
			will(returnValue(singletonMap(messageId1, DELIVERED)));
			// Get the message and its metadata to deliver
			oneOf(db).getMessage(txn, messageId);
			will(returnValue(message));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn, messageId);
			will(returnValue(new Metadata()));
			// Deliver the message
			oneOf(hook).incomingMessage(txn, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn, messageId);
			will(returnValue(singletonMap(messageId2, PENDING)));
			// Check whether the dependent is ready to deliver
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).getMessageState(txn1, messageId2);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn1, messageId2);
			will(returnValue(singletonMap(messageId1, DELIVERED)));
			// Get the dependent and its metadata to deliver
			oneOf(db).getMessage(txn1, messageId2);
			will(returnValue(message2));
			oneOf(db).getGroup(txn1, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn1, messageId2);
			will(returnValue(metadata));
			// Deliver the dependent
			oneOf(hook).incomingMessage(txn1, message2, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId2, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId2);
			will(returnValue(emptyMap()));
		}});

		expectGetMessagesToShare();

		vm.startService();
	}

	@Test
	public void testMessagesAreSharedAtStartup() throws Exception {
		Transaction txn = new Transaction(null, false);
		Transaction txn1 = new Transaction(null, false);

		expectGetMessagesToValidate();
		expectGetPendingMessages();
		expectGetMessagesToShare(messageId);

		context.checking(new DbExpectations() {{
			// Share message and get dependencies
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).setMessageShared(txn, messageId);
			oneOf(db).getMessageDependencies(txn, messageId);
			will(returnValue(singletonMap(messageId2, DELIVERED)));
			// Share dependency
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).setMessageShared(txn1, messageId2);
			oneOf(db).getMessageDependencies(txn1, messageId2);
			will(returnValue(emptyMap()));
		}});

		vm.startService();
	}

	@Test
	public void testIncomingMessagesAreShared() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(singletonMap(messageId1, DELIVERED)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(emptyMap()));
			// Share message
			oneOf(db).setMessageShared(txn1, messageId);
			// Share dependencies
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).setMessageShared(txn2, messageId1);
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testValidationContinuesAfterNoSuchMessageException()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, true);
		Transaction txn2 = new Transaction(null, false);

		expectGetMessagesToValidate(messageId, messageId1);

		context.checking(new DbExpectations() {{
			// Load the first raw message - *gasp* it's gone!
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessage(txn, messageId);
			will(throwException(new NoSuchMessageException()));
			// Load the second raw message and group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn1));
			oneOf(db).getMessage(txn1, messageId1);
			will(returnValue(message1));
			oneOf(db).getGroup(txn1, groupId);
			will(returnValue(group));
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(throwException(new InvalidMessageException()));
			// Invalidate the second message
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn2, messageId1, INVALID);
			oneOf(db).deleteMessage(txn2, messageId1);
			oneOf(db).deleteMessageMetadata(txn2, messageId1);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(emptyMap()));
		}});

		expectGetPendingMessages();
		expectGetMessagesToShare();

		vm.startService();
	}

	@Test
	public void testValidationContinuesAfterNoSuchGroupException()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, true);
		Transaction txn2 = new Transaction(null, false);

		expectGetMessagesToValidate(messageId, messageId1);

		context.checking(new DbExpectations() {{
			// Load the first raw message
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessage(txn, messageId);
			will(returnValue(message));
			// Load the group - *gasp* it's gone!
			oneOf(db).getGroup(txn, groupId);
			will(throwException(new NoSuchGroupException()));
			// Load the second raw message and group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn1));
			oneOf(db).getMessage(txn1, messageId1);
			will(returnValue(message1));
			oneOf(db).getGroup(txn1, groupId);
			will(returnValue(group));
			// Validate the second message: invalid
			oneOf(validator).validateMessage(message1, group);
			will(throwException(new InvalidMessageException()));
			// Store the validation result for the second message
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn2, messageId1, INVALID);
			oneOf(db).deleteMessage(txn2, messageId1);
			oneOf(db).deleteMessageMetadata(txn2, messageId1);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(emptyMap()));
		}});

		expectGetPendingMessages();
		expectGetMessagesToShare();

		vm.startService();
	}

	@Test
	public void testNonLocalMessagesAreValidatedWhenAdded() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testLocalMessagesAreNotValidatedWhenAdded() {
		vm.eventOccurred(new MessageAddedEvent(message, null));
	}

	@Test
	public void testMessagesWithUndeliveredDependenciesArePending()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(singletonMap(messageId1, UNKNOWN)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			oneOf(db).setMessageState(txn1, messageId, PENDING);
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testMessagesWithDeliveredDependenciesGetDelivered()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(singletonMap(messageId1, DELIVERED)));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testMessagesWithInvalidDependenciesAreInvalid()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResultWithDependencies));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).addMessageDependencies(txn1, message,
					validResultWithDependencies.getDependencies());
			// Check for invalid dependencies
			oneOf(db).getMessageDependencies(txn1, messageId);
			will(returnValue(singletonMap(messageId1, INVALID)));
			// Invalidate message
			oneOf(db).getMessageState(txn1, messageId);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn1, messageId, INVALID);
			oneOf(db).deleteMessage(txn1, messageId);
			oneOf(db).deleteMessageMetadata(txn1, messageId);
			// Recursively invalidate dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(singletonMap(messageId2, UNKNOWN)));
			// Invalidate dependent in a new transaction
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId2);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn2, messageId2, INVALID);
			oneOf(db).deleteMessage(txn2, messageId2);
			oneOf(db).deleteMessageMetadata(txn2, messageId2);
			oneOf(db).getMessageDependents(txn2, messageId2);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testRecursiveInvalidation() throws Exception {
		MessageId messageId3 = new MessageId(getRandomId());
		MessageId messageId4 = new MessageId(getRandomId());
		Map<MessageId, MessageState> twoDependents = new LinkedHashMap<>();
		twoDependents.put(messageId1, PENDING);
		twoDependents.put(messageId2, PENDING);
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, false);
		Transaction txn3 = new Transaction(null, false);
		Transaction txn4 = new Transaction(null, false);
		Transaction txn5 = new Transaction(null, false);
		Transaction txn6 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: invalid
			oneOf(validator).validateMessage(message, group);
			will(throwException(new InvalidMessageException()));
			// Invalidate the message
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).getMessageState(txn1, messageId);
			will(returnValue(UNKNOWN));
			oneOf(db).setMessageState(txn1, messageId, INVALID);
			oneOf(db).deleteMessage(txn1, messageId);
			oneOf(db).deleteMessageMetadata(txn1, messageId);
			// The message has two dependents: 1 and 2
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(twoDependents));
			// Invalidate message 1
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn2, messageId1, INVALID);
			oneOf(db).deleteMessage(txn2, messageId1);
			oneOf(db).deleteMessageMetadata(txn2, messageId1);
			// Message 1 has one dependent: 3
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(singletonMap(messageId3, PENDING)));
			// Invalidate message 2
			oneOf(db).transaction(with(false), withDbRunnable(txn3));
			oneOf(db).getMessageState(txn3, messageId2);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn3, messageId2, INVALID);
			oneOf(db).deleteMessage(txn3, messageId2);
			oneOf(db).deleteMessageMetadata(txn3, messageId2);
			// Message 2 has one dependent: 3 (same dependent as 1)
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(singletonMap(messageId3, PENDING)));
			// Invalidate message 3 (via 1)
			oneOf(db).transaction(with(false), withDbRunnable(txn4));
			oneOf(db).getMessageState(txn4, messageId3);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn4, messageId3, INVALID);
			oneOf(db).deleteMessage(txn4, messageId3);
			oneOf(db).deleteMessageMetadata(txn4, messageId3);
			// Message 3 has one dependent: 4
			oneOf(db).getMessageDependents(txn4, messageId3);
			will(returnValue(singletonMap(messageId4, PENDING)));
			// Invalidate message 3 (again, via 2)
			oneOf(db).transaction(with(false), withDbRunnable(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(INVALID)); // Already invalidated
			// Invalidate message 4 (via 1 and 3)
			oneOf(db).transaction(with(false), withDbRunnable(txn6));
			oneOf(db).getMessageState(txn6, messageId4);
			will(returnValue(PENDING));
			oneOf(db).setMessageState(txn6, messageId4, INVALID);
			oneOf(db).deleteMessage(txn6, messageId4);
			oneOf(db).deleteMessageMetadata(txn6, messageId4);
			// Message 4 has no dependents
			oneOf(db).getMessageDependents(txn6, messageId4);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testPendingDependentsGetDelivered() throws Exception {
		Message message3 = getMessage(groupId);
		Message message4 = getMessage(groupId);
		MessageId messageId3 = message3.getId();
		MessageId messageId4 = message4.getId();
		Map<MessageId, MessageState> twoDependents = new LinkedHashMap<>();
		twoDependents.put(messageId1, PENDING);
		twoDependents.put(messageId2, PENDING);
		Map<MessageId, MessageState> twoDependencies = new LinkedHashMap<>();
		twoDependencies.put(messageId1, DELIVERED);
		twoDependencies.put(messageId2, DELIVERED);
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, false);
		Transaction txn3 = new Transaction(null, false);
		Transaction txn4 = new Transaction(null, false);
		Transaction txn5 = new Transaction(null, false);
		Transaction txn6 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// The message has two pending dependents: 1 and 2
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(twoDependents));
			// Check whether message 1 is ready to be delivered
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(singletonMap(messageId, DELIVERED)));
			// Get message 1 and its metadata
			oneOf(db).getMessage(txn2, messageId1);
			will(returnValue(message1));
			oneOf(db).getGroup(txn2, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn2, messageId1);
			will(returnValue(metadata));
			// Deliver message 1
			oneOf(hook).incomingMessage(txn2, message1, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn2, messageId1, DELIVERED);
			// Message 1 has one pending dependent: 3
			oneOf(db).getMessageDependents(txn2, messageId1);
			will(returnValue(singletonMap(messageId3, PENDING)));
			// Check whether message 2 is ready to be delivered
			oneOf(db).transaction(with(false), withDbRunnable(txn3));
			oneOf(db).getMessageState(txn3, messageId2);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn3, messageId2);
			will(returnValue(singletonMap(messageId, DELIVERED)));
			// Get message 2 and its metadata
			oneOf(db).getMessage(txn3, messageId2);
			will(returnValue(message2));
			oneOf(db).getGroup(txn3, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn3, messageId2);
			will(returnValue(metadata));
			// Deliver message 2
			oneOf(hook).incomingMessage(txn3, message2, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn3, messageId2, DELIVERED);
			// Message 2 has one pending dependent: 3 (same dependent as 1)
			oneOf(db).getMessageDependents(txn3, messageId2);
			will(returnValue(singletonMap(messageId3, PENDING)));
			// Check whether message 3 is ready to be delivered (via 1)
			oneOf(db).transaction(with(false), withDbRunnable(txn4));
			oneOf(db).getMessageState(txn4, messageId3);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn4, messageId3);
			will(returnValue(twoDependencies));
			// Get message 3 and its metadata
			oneOf(db).getMessage(txn4, messageId3);
			will(returnValue(message3));
			oneOf(db).getGroup(txn4, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn4, messageId3);
			will(returnValue(metadata));
			// Deliver message 3
			oneOf(hook).incomingMessage(txn4, message3, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn4, messageId3, DELIVERED);
			// Message 3 has one pending dependent: 4
			oneOf(db).getMessageDependents(txn4, messageId3);
			will(returnValue(singletonMap(messageId4, PENDING)));
			// Check whether message 3 is ready to be delivered (again, via 2)
			oneOf(db).transaction(with(false), withDbRunnable(txn5));
			oneOf(db).getMessageState(txn5, messageId3);
			will(returnValue(DELIVERED)); // Already delivered
			// Check whether message 4 is ready to be delivered (via 1 and 3)
			oneOf(db).transaction(with(false), withDbRunnable(txn6));
			oneOf(db).getMessageState(txn6, messageId4);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn6, messageId4);
			will(returnValue(singletonMap(messageId3, DELIVERED)));
			// Get message 4 and its metadata
			oneOf(db).getMessage(txn6, messageId4);
			will(returnValue(message4));
			oneOf(db).getGroup(txn6, groupId);
			will(returnValue(group));
			oneOf(db).getMessageMetadataForValidator(txn6, messageId4);
			will(returnValue(metadata));
			// Deliver message 4
			oneOf(hook).incomingMessage(txn6, message4, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn6, messageId4, DELIVERED);
			// Message 4 has no pending dependents
			oneOf(db).getMessageDependents(txn6, messageId4);
			will(returnValue(emptyMap()));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	@Test
	public void testOnlyReadyPendingDependentsGetDelivered() throws Exception {
		Map<MessageId, MessageState> twoDependencies = new LinkedHashMap<>();
		twoDependencies.put(messageId, DELIVERED);
		twoDependencies.put(messageId2, UNKNOWN);
		Transaction txn = new Transaction(null, true);
		Transaction txn1 = new Transaction(null, false);
		Transaction txn2 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Load the group
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroup(txn, groupId);
			will(returnValue(group));
			// Validate the message: valid
			oneOf(validator).validateMessage(message, group);
			will(returnValue(validResult));
			// Store the validation result
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			oneOf(db).mergeMessageMetadata(txn1, messageId, metadata);
			// Deliver the message
			oneOf(hook).incomingMessage(txn1, message, metadata);
			will(returnValue(ACCEPT_DO_NOT_SHARE));
			oneOf(db).setMessageState(txn1, messageId, DELIVERED);
			// Get any pending dependents
			oneOf(db).getMessageDependents(txn1, messageId);
			will(returnValue(singletonMap(messageId1, PENDING)));
			// Check whether the pending dependent is ready to be delivered
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(db).getMessageState(txn2, messageId1);
			will(returnValue(PENDING));
			oneOf(db).getMessageDependencies(txn2, messageId1);
			will(returnValue(twoDependencies));
		}});

		vm.eventOccurred(new MessageAddedEvent(message, contactId));
	}

	private void expectGetMessagesToValidate(MessageId... ids)
			throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessagesToValidate(txn);
			will(returnValue(asList(ids)));
		}});
	}

	private void expectGetPendingMessages(MessageId... ids) throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getPendingMessages(txn);
			will(returnValue(asList(ids)));
		}});
	}

	private void expectGetMessagesToShare(MessageId... ids) throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessagesToShare(txn);
			will(returnValue(asList(ids)));
		}});
	}
}
