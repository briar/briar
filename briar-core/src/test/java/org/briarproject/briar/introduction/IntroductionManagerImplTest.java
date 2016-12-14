package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.test.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.junit.Assert.assertFalse;

public class IntroductionManagerImplTest extends BriarTestCase {

	private final Mockery context;
	private final IntroductionManagerImpl introductionManager;
	private final IntroducerManager introducerManager;
	private final IntroduceeManager introduceeManager;
	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final MessageTracker messageTracker;
	private final IntroductionGroupFactory introductionGroupFactory;
	private final SessionId sessionId = new SessionId(TestUtils.getRandomId());
	private final MessageId storageId = new MessageId(sessionId.getBytes());
	private final long time = 42L;
	private final Contact introducee1;
	private final Contact introducee2;
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

		ClientId clientId = new ClientId(TestUtils.getRandomString(5));
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
		clientHelper = context.mock(ClientHelper.class);
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		messageTracker = context.mock(MessageTracker.class);
		introductionGroupFactory = context.mock(IntroductionGroupFactory.class);

		introductionManager = new IntroductionManagerImpl(db, clientHelper,
				metadataParser, messageTracker, introducerManager,
				introduceeManager, introductionGroupFactory);
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
			// get both introduction groups
			oneOf(introductionGroupFactory)
					.createIntroductionGroup(introducee1);
			will(returnValue(introductionGroup1));
			oneOf(introductionGroupFactory)
					.createIntroductionGroup(introducee2);
			will(returnValue(introductionGroup2));
			// track message for group 1
			oneOf(messageTracker).trackMessage(txn,
					introductionGroup1.getId(), time, true);
			// track message for group 2
			oneOf(messageTracker).trackMessage(txn,
					introductionGroup2.getId(), time, true);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.makeIntroduction(introducee1, introducee2, null, time);

		context.assertIsSatisfied();
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
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, storageId);
			will(returnValue(state));
			oneOf(introduceeManager).acceptIntroduction(txn, state, time);
			// track message
			oneOf(messageTracker).trackMessage(txn,
					introductionGroup1.getId(), time, true);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.acceptIntroduction(introducee1.getId(), sessionId, time);

		context.assertIsSatisfied();
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
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, storageId);
			will(returnValue(state));
			oneOf(introduceeManager).declineIntroduction(txn, state, time);
			// track message
			oneOf(messageTracker).trackMessage(txn,
					introductionGroup1.getId(), time, true);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager
				.declineIntroduction(introducee1.getId(), sessionId, time);

		context.assertIsSatisfied();
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
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		introductionManager.getIntroductionMessages(introducee1.getId());

		context.assertIsSatisfied();
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
			// track message
			oneOf(messageTracker).trackIncomingMessage(txn, message1);
		}});

		introductionManager
				.incomingMessage(txn, message1, new BdfList(), msg);

		context.assertIsSatisfied();
		assertFalse(txn.isCommitted());
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
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn, storageId);
			will(returnValue(state));
			oneOf(introducerManager).incomingMessage(txn, state, msg);
			// track message
			oneOf(messageTracker).trackIncomingMessage(txn, message1);
		}});

		introductionManager
				.incomingMessage(txn, message1, new BdfList(), msg);

		context.assertIsSatisfied();
		assertFalse(txn.isCommitted());
	}


}
