package org.briarproject.introduction;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.system.Clock;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.junit.Assert.assertFalse;

public class IntroductionManagerImplTest extends BriarTestCase {

	final Mockery context;
	final IntroductionManagerImpl introductionManager;
	final IntroducerManager introducerManager;
	final IntroduceeManager introduceeManager;
	final DatabaseComponent db;
	final PrivateGroupFactory privateGroupFactory;
	final ClientHelper clientHelper;
	final MetadataEncoder metadataEncoder;
	final MessageQueueManager messageQueueManager;
	final IntroductionGroupFactory introductionGroupFactory;
	final Clock clock;
	final SessionId sessionId = new SessionId(TestUtils.getRandomId());
	final long time = 42L;
	final Contact introducee1;
	final Contact introducee2;
	final Group localGroup0;
	final Group introductionGroup1;
	final Group introductionGroup2;
	final Message message1;
	Transaction txn;

	public IntroductionManagerImplTest() {
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Introducee1",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		AuthorId localAuthorId1 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId1 = new ContactId(234);
		introducee1 = new Contact(contactId1, author1, localAuthorId1, true);

		AuthorId authorId2 = new AuthorId(TestUtils.getRandomId());
		Author author2 = new Author(authorId2, "Introducee2",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		AuthorId localAuthorId2 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId2 = new ContactId(235);
		introducee2 = new Contact(contactId2, author2, localAuthorId2, true);

		ClientId clientId = new ClientId(TestUtils.getRandomId());
		localGroup0 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);
		introductionGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);
		introductionGroup2 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);

		message1 = new Message(
				new MessageId(TestUtils.getRandomId()),
				introductionGroup1.getId(),
				time,
				TestUtils.getRandomBytes(MESSAGE_HEADER_LENGTH + 1)
		);

		// mock ALL THE THINGS!!!
		context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		introducerManager = context.mock(IntroducerManager.class);
		introduceeManager = context.mock(IntroduceeManager.class);
		db = context.mock(DatabaseComponent.class);
		privateGroupFactory = context.mock(PrivateGroupFactory.class);
		clientHelper = context.mock(ClientHelper.class);
		metadataEncoder =
				context.mock(MetadataEncoder.class);
		messageQueueManager =
				context.mock(MessageQueueManager.class);
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		introductionGroupFactory = context.mock(IntroductionGroupFactory.class);
		clock = context.mock(Clock.class);

		introductionManager = new IntroductionManagerImpl(
				db, clientHelper, metadataParser, introducerManager,
				introduceeManager, introductionGroupFactory
		);
	}

	@Test
	public void testMakeIntroduction() throws DbException, FormatException {
		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(introducerManager)
					.makeIntroduction(txn, introducee1, introducee2, null,
							time);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.makeIntroduction(introducee1, introducee2, null, time);

		context.assertIsSatisfied();
		assertTrue(txn.isComplete());
	}

	@Test
	public void testAcceptIntroduction() throws DbException, FormatException {
		final BdfDictionary state = BdfDictionary.of(
				new BdfEntry(GROUP_ID_1, introductionGroup1.getId()),
				new BdfEntry(GROUP_ID_2, introductionGroup2.getId())
		);
		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, introducee1.getId());
			will(returnValue(introducee1));
			oneOf(introductionGroupFactory).createIntroductionGroup(introducee1);
			will(returnValue(introductionGroup1));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, sessionId);
			will(returnValue(state));
			oneOf(introduceeManager).acceptIntroduction(txn, state, time);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.acceptIntroduction(introducee1.getId(), sessionId, time);

		context.assertIsSatisfied();
		assertTrue(txn.isComplete());
	}

	@Test
	public void testDeclineIntroduction() throws DbException, FormatException {
		final BdfDictionary state = BdfDictionary.of(
				new BdfEntry(GROUP_ID_1, introductionGroup1.getId()),
				new BdfEntry(GROUP_ID_2, introductionGroup2.getId())
		);
		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, introducee1.getId());
			will(returnValue(introducee1));
			oneOf(introductionGroupFactory).createIntroductionGroup(introducee1);
			will(returnValue(introductionGroup1));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, sessionId);
			will(returnValue(state));
			oneOf(introduceeManager).declineIntroduction(txn, state, time);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.declineIntroduction(introducee1.getId(), sessionId, time);

		context.assertIsSatisfied();
		assertTrue(txn.isComplete());
	}

	@Test
	public void testGetIntroductionMessages()
			throws DbException, FormatException {

		final Map<MessageId, BdfDictionary> metadata = Collections.emptyMap();
		final Collection<MessageStatus> statuses = Collections.emptyList();
		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContact(txn, introducee1.getId());
			will(returnValue(introducee1));
			oneOf(introductionGroupFactory).createIntroductionGroup(introducee1);
			will(returnValue(introductionGroup1));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					introductionGroup1.getId());
			will(returnValue(metadata));
			oneOf(db).getMessageStatus(txn, introducee1.getId(),
					introductionGroup1.getId());
			will(returnValue(statuses));
			oneOf(db).endTransaction(txn);
		}});

		introductionManager.getIntroductionMessages(introducee1.getId());

		context.assertIsSatisfied();
		assertTrue(txn.isComplete());
	}

	@Test
	public void testIncomingRequestMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_REQUEST);

		final BdfDictionary state = new BdfDictionary();
		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(introduceeManager)
					.initialize(txn, introductionGroup1.getId(), msg);
			will(returnValue(state));
			oneOf(introduceeManager)
					.incomingMessage(txn, state, msg);
		}});

		introductionManager
				.incomingReadableMessage(txn, message1, new BdfList(), msg);

		context.assertIsSatisfied();
		assertFalse(txn.isComplete());
	}

	@Test
	public void testIncomingResponseMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_RESPONSE),
				new BdfEntry(SESSION_ID, sessionId)
		);

		final BdfDictionary state = new BdfDictionary();
		state.put(ROLE, ROLE_INTRODUCER);
		state.put(GROUP_ID_1, introductionGroup1.getId());
		state.put(GROUP_ID_2, introductionGroup2.getId());

		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, sessionId);
			will(returnValue(state));
			oneOf(introducerManager).incomingMessage(txn, state, msg);
		}});

		introductionManager
				.incomingReadableMessage(txn, message1, new BdfList(), msg);

		context.assertIsSatisfied();
		assertFalse(txn.isComplete());
	}


}
