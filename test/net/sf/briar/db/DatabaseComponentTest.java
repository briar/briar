package net.sf.briar.db;

import java.util.Collections;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.db.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.Rating;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Bundle;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.protocol.MessageImpl;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Provider;

public abstract class DatabaseComponentTest extends TestCase {

	protected final Object txn = new Object();
	protected final AuthorId authorId;
	protected final ContactId contactId;
	protected final GroupId groupId;
	protected final MessageId messageId, parentId;
	private final long timestamp;
	private final int size;
	private final byte[] body;
	private final Message message;

	public DatabaseComponentTest() {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		contactId = new ContactId(123);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		parentId = new MessageId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		size = 1234;
		body = new byte[size];
		message = new MessageImpl(messageId, MessageId.NONE, groupId, authorId,
				timestamp, body);
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<Batch> batchProvider);

	@Test
	public void testSimpleCalls() throws DbException {
		final Set<GroupId> subs = Collections.singleton(groupId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			oneOf(database).open(false);
			oneOf(cleaner).startCleaning();
			// getRating(authorId)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).commitTransaction(txn);
			// addContact(contactId)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).addContact(txn, contactId);
			oneOf(database).commitTransaction(txn);
			// subscribe(groupId)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).addSubscription(txn, groupId);
			oneOf(database).commitTransaction(txn);
			// getSubscriptions()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getSubscriptions(txn);
			will(returnValue(subs));
			oneOf(database).commitTransaction(txn);
			// unsubscribe(groupId)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).removeSubscription(txn, groupId);
			oneOf(database).commitTransaction(txn);
			// removeContact(contactId)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).removeContact(txn, contactId);
			oneOf(database).commitTransaction(txn);
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.open(false);
		assertEquals(Rating.UNRATED, db.getRating(authorId));
		db.addContact(contactId);
		db.subscribe(groupId);
		assertEquals(Collections.singleton(groupId), db.getSubscriptions());
		db.unsubscribe(groupId);
		db.removeContact(contactId);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testNoParentStopsBackwardInclusion() throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// Backward inclusion stops when the message has no parent
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testMissingParentStopsBackwardInclusion() throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			// The parent isn't in the DB
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testChangingGroupsStopsBackwardInclusion() throws DbException {
		final GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists and is in the database
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			// The parent is in a different group
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId1));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testUnaffectedParentStopsBackwardInclusion()
	throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId));
			// The parent is already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, parentId, 2);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testAffectedParentContinuesBackwardInclusion()
	throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId));
			// The parent is not already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, parentId, 1);
			oneOf(database).getParent(txn, parentId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testMessagesAreNotStoredUnlessSubscribed()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateMessagesAreNotStored() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocallyGeneratedMessage() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singleton(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The author is unrated and there are no sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingASendableMessageTriggersBackwardInclusion()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singleton(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The author is rated GOOD and there are two sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(2));
			oneOf(database).setSendability(txn, messageId, 3);
			// The sendability of the message's ancestors should be updated
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBundleThrowsExceptionIfContactIsMissing()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		final Bundle bundle = context.mock(Bundle.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		try {
			db.generateBundle(contactId, bundle);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}
}
