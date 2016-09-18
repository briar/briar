package org.briarproject.introduction;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.introduction.IntroduceeProtocolState;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.security.SecureRandom;

import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_LOCAL_RESPONSE;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_RESPONSES;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.introduction.IntroduceeSessionState.fromBdfDictionary;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IntroduceeManagerTest extends BriarTestCase {

	private final Mockery context;
	private final IntroduceeManager introduceeManager;
	private final DatabaseComponent db;
	private final CryptoComponent cryptoComponent;
	private final ClientHelper clientHelper;
	private final IntroductionGroupFactory introductionGroupFactory;
	private final MessageSender messageSender;
	private final TransportPropertyManager transportPropertyManager;
	private final AuthorFactory authorFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final Clock clock;
	private final Contact introducer;
	private final Contact introducee1;
	private final Contact introducee2;
	private final Group localGroup1;
	private final Group introductionGroup1;
	private final Transaction txn;
	private final long time = 42L;
	private final Message localStateMessage;
	private final ClientId clientId;
	private final SessionId sessionId;
	private final Message message1;

	public IntroduceeManagerTest() {
		context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		messageSender = context.mock(MessageSender.class);
		db = context.mock(DatabaseComponent.class);
		cryptoComponent = context.mock(CryptoComponent.class);
		clientHelper = context.mock(ClientHelper.class);
		clock = context.mock(Clock.class);
		introductionGroupFactory =
				context.mock(IntroductionGroupFactory.class);
		transportPropertyManager = context.mock(TransportPropertyManager.class);
		authorFactory = context.mock(AuthorFactory.class);
		contactManager = context.mock(ContactManager.class);
		identityManager = context.mock(IdentityManager.class);

		introduceeManager = new IntroduceeManager(messageSender, db,
				clientHelper, clock, cryptoComponent, transportPropertyManager,
				authorFactory, contactManager, identityManager,
				introductionGroupFactory);

		AuthorId authorId0 = new AuthorId(TestUtils.getRandomId());
		Author author0 = new Author(authorId0, "Introducer",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		AuthorId localAuthorId = new AuthorId(TestUtils.getRandomId());
		ContactId contactId0 = new ContactId(234);
		introducer =
				new Contact(contactId0, author0, localAuthorId, true, true);

		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Introducee1",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		AuthorId localAuthorId1 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId1 = new ContactId(234);
		introducee1 =
				new Contact(contactId1, author1, localAuthorId1, true, true);

		AuthorId authorId2 = new AuthorId(TestUtils.getRandomId());
		Author author2 = new Author(authorId2, "Introducee2",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		ContactId contactId2 = new ContactId(235);
		introducee2 =
				new Contact(contactId2, author2, localAuthorId, true, true);

		clientId = IntroductionManagerImpl.CLIENT_ID;
		localGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);
		introductionGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);

		sessionId = new SessionId(TestUtils.getRandomId());
		localStateMessage = new Message(
				new MessageId(TestUtils.getRandomId()),
				localGroup1.getId(),
				time,
				TestUtils.getRandomBytes(MESSAGE_HEADER_LENGTH + 1)
		);
		message1 = new Message(
				new MessageId(TestUtils.getRandomId()),
				introductionGroup1.getId(),
				time,
				TestUtils.getRandomBytes(MESSAGE_HEADER_LENGTH + 1)
		);

		txn = new Transaction(null, false);
	}

	@Test
	public void testIncomingRequestMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_REQUEST);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());

		final IntroduceeSessionState state =
				initializeSessionState(txn, sessionId,
						introductionGroup1.getId(), msg);
		state.setIntroducedName(msg.getString(NAME));
		state.setIntroducedPublicKey(msg.getRaw(PUBLIC_KEY));

		final BdfDictionary statedict = state.toBdfDictionary();
		statedict.put(STATE, AWAIT_RESPONSES.getValue());

		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeMessageMetadata(txn,
					localStateMessage.getId(), statedict);
		}});

		introduceeManager.incomingMessage(txn, state, msg);

		context.assertIsSatisfied();

		assertFalse(txn.isComplete());
	}

	@Test
	public void testIncomingResponseMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_RESPONSE);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());


		final IntroduceeSessionState state =
				initializeSessionState(txn, sessionId,
						introductionGroup1.getId(), msg);
		state.setTheirTime(time);
		state.setOurTransportProperties(new BdfDictionary());
		final BdfDictionary statedict = state.toBdfDictionary();
		state.setState(AWAIT_RESPONSES);
		statedict.put(STATE, AWAIT_LOCAL_RESPONSE.getValue());
		statedict.put(ACCEPT, true);
		statedict.put(E_PUBLIC_KEY,
					  TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		statedict.put(NOT_OUR_RESPONSE, message1.getId().getBytes());

		// turn request message into a response
		msg.put(ACCEPT, true);
		msg.put(TIME, time);
		msg.put(E_PUBLIC_KEY, statedict.getRaw(E_PUBLIC_KEY));
		msg.put(TRANSPORT, new BdfDictionary());



		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeMessageMetadata(txn,
					localStateMessage.getId(), statedict);
		}});

		introduceeManager.incomingMessage(txn, state, msg);

		context.assertIsSatisfied();

		assertFalse(txn.isComplete());
	}

	@Test
	public void testInitialSerialization() throws DbException, FormatException {
		IntroduceeSessionState state = initializeDefaultSessionState();

		BdfDictionary statedict = state.toBdfDictionary();
		assertEquals(statedict, fromBdfDictionary(statedict).toBdfDictionary());
	}

	@Test
	public void testFullSerialization() throws DbException, FormatException {
		IntroduceeSessionState state = initializeDefaultSessionState();

		state.setOurMac(TestUtils.getRandomBytes(42));
		// TODO use ALL setters here to make the test cover everything
		state.setState(IntroduceeProtocolState.AWAIT_ACK);
		state.setAccept(true);
		state.setAnswered(true);
		state.setOurTime(-1L);
		state.setTheirTime(-2L);
		state.setMessage("");
		state.setTheirEphemeralPublicKey(TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		state.setOurTransportProperties(new BdfDictionary());
		state.setContactExists(false);
		state.setRemoteAuthorId(new AuthorId(TestUtils.getRandomId()));
		state.setRemoteAuthorIsUs(false);
		state.setTheirResponseId(TestUtils.getRandomId());
		state.setTask(-1);
		state.setIntroducedName(TestUtils.getRandomString(MAX_AUTHOR_NAME_LENGTH));
		state.setOurPublicKey(TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		state.setOurPrivateKey(TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		state.setIntroducedPublicKey(TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		state.setMac(TestUtils.getRandomBytes(MAC_LENGTH));
		state.setSignature(TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH));
		state.setOurSignature(TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH));
		state.setOurTransport(new BdfDictionary());
		state.setTheirNonce(TestUtils.getRandomBytes(32));
		state.setTheirMacKey(TestUtils.getRandomBytes(32));

		BdfDictionary statedict = state.toBdfDictionary();
		assertEquals(statedict, fromBdfDictionary(statedict).toBdfDictionary());
	}

	private IntroduceeSessionState initializeDefaultSessionState()
			throws DbException, FormatException {
		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_REQUEST);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());
		msg.put(TRANSPORT, new BdfDictionary());

		return initializeSessionState(txn, sessionId,
				introductionGroup1.getId(), msg);
	}

	private IntroduceeSessionState initializeSessionState(final Transaction txn,
			final SessionId sessionId, final GroupId groupId,
			final BdfDictionary msg) throws DbException, FormatException {

		final SecureRandom secureRandom = context.mock(SecureRandom.class);
		final Bytes salt = new Bytes(new byte[64]);
		final BdfDictionary groupMetadata = BdfDictionary.of(
				new BdfEntry(CONTACT, introducee1.getId().getInt())
		);
		final boolean contactExists = true;
		final IntroduceeSessionState state = new IntroduceeSessionState(
				localStateMessage.getId(), 
				sessionId, groupId, introducer.getId(),
				introducer.getAuthor().getId(), introducer.getAuthor().getName(),
				introducer.getLocalAuthorId(), AWAIT_REQUEST);

		state.setContactExists(true);
		state.setRemoteAuthorIsUs(false);
		state.setRemoteAuthorId(introducee2.getAuthor().getId());
		state.setIntroducedPublicKey(introducee2.getAuthor().getPublicKey());
		final BdfDictionary statedict = state.toBdfDictionary();

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
			oneOf(cryptoComponent).getSecureRandom();
			will(returnValue(secureRandom));
			oneOf(secureRandom).nextBytes(salt.getBytes());
			oneOf(introductionGroupFactory).createLocalGroup();
			will(returnValue(localGroup1));
			oneOf(clientHelper)
					.createMessage(localGroup1.getId(), time, BdfList.of(salt));
			will(returnValue(localStateMessage));

			// who is making the introduction? who is the introducer?
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					groupId);
			will(returnValue(groupMetadata));
			oneOf(db).getContact(txn, introducer.getId());
			will(returnValue(introducer));

			// create remote author to check if contact exists
			oneOf(authorFactory).createAuthor(introducee2.getAuthor().getName(),
					introducee2.getAuthor().getPublicKey());
			will(returnValue(introducee2.getAuthor()));
			oneOf(contactManager)
					.contactExists(txn, introducee2.getAuthor().getId(),
							introducer.getLocalAuthorId());
			will(returnValue(contactExists));

			// checks if remote author is one of our identities
			oneOf(db).containsLocalAuthor(txn, introducee2.getAuthor().getId());
			will(returnValue(false));

			// store session state
			oneOf(clientHelper)
					.addLocalMessage(txn, localStateMessage, statedict,
							false);
		}});

		IntroduceeSessionState result = introduceeManager.initialize(txn,
				sessionId, groupId, msg);

		context.assertIsSatisfied();
		return result;
	}

}
