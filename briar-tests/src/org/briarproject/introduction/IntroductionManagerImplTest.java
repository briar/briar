package org.briarproject.introduction;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.SessionId;
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
import org.briarproject.api.introduction.IntroducerProtocolState;
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
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.NO_TASK;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.junit.Assert.assertFalse;

public class IntroductionManagerImplTest extends BriarTestCase {

	private final Mockery context;
	private final IntroductionManagerImpl introductionManager;
	private final IntroducerManager introducerManager;
	private final IntroduceeManager introduceeManager;
	private final DatabaseComponent db;
	private final PrivateGroupFactory privateGroupFactory;
	private final ClientHelper clientHelper;
	private final MetadataEncoder metadataEncoder;
	private final MessageQueueManager messageQueueManager;
	private final IntroductionGroupFactory introductionGroupFactory;
	private final Clock clock;
	private final SessionId sessionId = new SessionId(TestUtils.getRandomId());
	private final long time = 42L;
	private final Contact introducee1;
	private final Contact introducee2;
	private final Group localGroup0;
	private final Group introductionGroup1;
	private final Group introductionGroup2;
	private final Message message1;
	private Transaction txn;

	public IntroductionManagerImplTest() {
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Introducee1",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		AuthorId localAuthorId1 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId1 = new ContactId(234);
		introducee1 =
				new Contact(contactId1, author1, localAuthorId1, true, true);

		AuthorId authorId2 = new AuthorId(TestUtils.getRandomId());
		Author author2 = new Author(authorId2, "Introducee2",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		AuthorId localAuthorId2 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId2 = new ContactId(235);
		introducee2 =
				new Contact(contactId2, author2, localAuthorId2, true, true);

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
				new BdfEntry(ROLE, ROLE_INTRODUCEE),
				new BdfEntry(GROUP_ID_1, introductionGroup1.getId()),
				new BdfEntry(GROUP_ID_2, introductionGroup2.getId()),
				new BdfEntry(SESSION_ID, sessionId),
				new BdfEntry(STORAGE_ID, sessionId),
				new BdfEntry(AUTHOR_ID_1,introducee1.getAuthor().getId()),
				new BdfEntry(CONTACT_1, introducee1.getAuthor().getName()),
				new BdfEntry(CONTACT_ID_1, introducee1.getId().getInt()),
				new BdfEntry(AUTHOR_ID_2,introducee2.getAuthor().getId() ),
				new BdfEntry(CONTACT_2, introducee2.getAuthor().getName()),
				new BdfEntry(CONTACT_ID_2, introducee2.getId().getInt()),
				new BdfEntry(STATE, AWAIT_REQUEST.getValue()),
				new BdfEntry(TIME, time),
	            new BdfEntry(OUR_TIME, time),
	            new BdfEntry(NAME, introducee1.getAuthor().getName()),
				new BdfEntry(LOCAL_AUTHOR_ID, introducee1.getLocalAuthorId()),
	            new BdfEntry(INTRODUCER, introducee1.getAuthor().getName()),
	            new BdfEntry(EXISTS, false),
	            new BdfEntry(TASK, NO_TASK),
	            new BdfEntry(REMOTE_AUTHOR_IS_US, false)
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
			oneOf(introduceeManager).acceptIntroduction(with(equal(txn)),
					with(any(IntroduceeSessionState.class)), with(equal(time)));
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
				new BdfEntry(ROLE, ROLE_INTRODUCEE),
				new BdfEntry(GROUP_ID_1, introductionGroup1.getId()),
				new BdfEntry(GROUP_ID_2, introductionGroup2.getId()),
				new BdfEntry(SESSION_ID, sessionId),
				new BdfEntry(STORAGE_ID, sessionId),
				new BdfEntry(AUTHOR_ID_1,introducee1.getAuthor().getId()),
				new BdfEntry(CONTACT_1, introducee1.getAuthor().getName()),
				new BdfEntry(CONTACT_ID_1, introducee1.getId().getInt()),
				new BdfEntry(AUTHOR_ID_2,introducee2.getAuthor().getId() ),
				new BdfEntry(CONTACT_2, introducee2.getAuthor().getName()),
				new BdfEntry(CONTACT_ID_2, introducee2.getId().getInt()),
				new BdfEntry(STATE, AWAIT_REQUEST.getValue()),
				new BdfEntry(TIME, time),
	            new BdfEntry(OUR_TIME, time),
	            new BdfEntry(NAME, introducee1.getAuthor().getName()),
				new BdfEntry(LOCAL_AUTHOR_ID, introducee1.getLocalAuthorId()),
				new BdfEntry(INTRODUCER, introducee1.getAuthor().getName()),
				new BdfEntry(EXISTS, false),
				new BdfEntry(TASK, NO_TASK),
				new BdfEntry(REMOTE_AUTHOR_IS_US, false)
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
			oneOf(introduceeManager).declineIntroduction(with(equal(txn)),
					with(any(IntroduceeSessionState.class)), with(equal(time)));
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

		final IntroduceeSessionState state = initializeIntroduceeSS();
		txn = new Transaction(null, false);
		final SessionId sessionId = new SessionId(TestUtils.getRandomId());
		msg.put(SESSION_ID, sessionId);

		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					new MessageId(sessionId.getBytes()));
			will(returnValue(state.toBdfDictionary()));
			oneOf(introduceeManager)
					.initialize(txn, sessionId, introductionGroup1.getId(),
							msg);
			will(returnValue(state));
			oneOf(introduceeManager).incomingMessage(txn, state, msg);
		}});

		introductionManager.incomingMessage(txn, message1, new BdfList(), msg);

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

		final IntroducerSessionState sessionState = initializeIntroducerSS();
		final BdfDictionary state = sessionState.toBdfDictionary();
		state.put(ROLE, ROLE_INTRODUCER);
		state.put(GROUP_ID_1, introductionGroup1.getId());
		state.put(GROUP_ID_2, introductionGroup2.getId());

		txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, sessionId);
			will(returnValue(state));
			oneOf(introducerManager).incomingMessage(with(equal(txn)),
					with(any(IntroducerSessionState.class)), with(equal(msg)));
		}});

		introductionManager
				.incomingMessage(txn, message1, new BdfList(), msg);

		context.assertIsSatisfied();
		assertFalse(txn.isComplete());
	}

	private IntroduceeSessionState initializeIntroduceeSS() {

		final ContactId cid = new ContactId(0);
		final AuthorId aid = new AuthorId(TestUtils.getRandomId());
		Author author = new Author(aid, "Introducer",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		final Contact introducer = new Contact(cid, author, aid, true, false);
		final IntroduceeSessionState state = new IntroduceeSessionState(
				new MessageId(TestUtils.getRandomId()),
				new SessionId(TestUtils.getRandomId()), 
				new GroupId(TestUtils.getRandomId()),
				introducer.getId(), introducer.getAuthor().getId(),
				introducer.getAuthor().getName(), introducer.getLocalAuthorId(),
				AWAIT_REQUEST);

		state.setContactExists(true);
		state.setRemoteAuthorIsUs(false);
		state.setRemoteAuthorId(introducee2.getAuthor().getId());
		state.setName(introducee2.getAuthor().getName());
		
		return  state;
	}

	private IntroducerSessionState initializeIntroducerSS() {
		final ContactId cid = new ContactId(0);
		final AuthorId aid = new AuthorId(TestUtils.getRandomId());
		Author author = new Author(aid, "Introducer",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		final Contact introducer = new Contact(cid, author, aid, true, false);
		return new IntroducerSessionState(
				new MessageId(TestUtils.getRandomId()),
				new SessionId(TestUtils.getRandomId()),
				new GroupId(TestUtils.getRandomId()),
				new GroupId(TestUtils.getRandomId()),
				introducer.getId(), introducer.getAuthor().getId(), introducer.getAuthor().getName(),
				introducer.getId(), introducer.getAuthor().getId(), introducer.getAuthor().getName(),
				IntroducerProtocolState.AWAIT_RESPONSES);
	}


}
