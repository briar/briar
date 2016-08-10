package org.briarproject.introduction;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
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
import static org.briarproject.api.introduction.IntroducerProtocolState.AWAIT_RESPONSES;
import static org.briarproject.api.introduction.IntroducerProtocolState.PREPARE_REQUESTS;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.junit.Assert.assertFalse;

public class IntroducerManagerTest extends BriarTestCase {

	final Mockery context;
	final IntroducerManager introducerManager;
	final CryptoComponent cryptoComponent;
	final ClientHelper clientHelper;
	final IntroductionGroupFactory introductionGroupFactory;
	final MessageSender messageSender;
	final Clock clock;
	final Contact introducee1;
	final Contact introducee2;
	final Group localGroup0;
	final Group introductionGroup1;
	final Group introductionGroup2;

	public IntroducerManagerTest() {
		context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		messageSender = context.mock(MessageSender.class);
		cryptoComponent = context.mock(CryptoComponent.class);
		clientHelper = context.mock(ClientHelper.class);
		clock = context.mock(Clock.class);
		introductionGroupFactory =
				context.mock(IntroductionGroupFactory.class);

		introducerManager =
				new IntroducerManager(messageSender, clientHelper, clock,
						cryptoComponent, introductionGroupFactory);

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
		AuthorId localAuthorId2 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId2 = new ContactId(235);
		introducee2 =
				new Contact(contactId2, author2, localAuthorId2, true, true);

		localGroup0 = new Group(new GroupId(TestUtils.getRandomId()),
				getClientId(), new byte[0]);
		introductionGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				getClientId(), new byte[0]);
		introductionGroup2 = new Group(new GroupId(TestUtils.getRandomId()),
				getClientId(), new byte[0]);

		context.assertIsSatisfied();
	}

	@Test
	public void testMakeIntroduction() throws DbException, FormatException {
		final Transaction txn = new Transaction(null, false);
		final long time = 42L;
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final SecureRandom secureRandom = context.mock(SecureRandom.class);
		final Bytes salt = new Bytes(new byte[64]);
		final Message msg = new Message(new MessageId(TestUtils.getRandomId()),
				localGroup0.getId(), time, TestUtils.getRandomBytes(64));
		final BdfDictionary state = new BdfDictionary();
		state.put(SESSION_ID, msg.getId());
		state.put(STORAGE_ID, msg.getId());
		state.put(STATE, PREPARE_REQUESTS.getValue());
		state.put(ROLE, ROLE_INTRODUCER);
		state.put(GROUP_ID_1, introductionGroup1.getId());
		state.put(GROUP_ID_2, introductionGroup2.getId());
		state.put(CONTACT_1, introducee1.getAuthor().getName());
		state.put(CONTACT_2, introducee2.getAuthor().getName());
		state.put(CONTACT_ID_1, introducee1.getId().getInt());
		state.put(CONTACT_ID_2, introducee2.getId().getInt());
		state.put(AUTHOR_ID_1, introducee1.getAuthor().getId());
		state.put(AUTHOR_ID_2, introducee2.getAuthor().getId());
		final BdfDictionary state2 = (BdfDictionary) state.clone();
		state2.put(STATE, AWAIT_RESPONSES.getValue());

		final BdfDictionary msg1 = new BdfDictionary();
		msg1.put(TYPE, TYPE_REQUEST);
		msg1.put(SESSION_ID, state.getRaw(SESSION_ID));
		msg1.put(GROUP_ID, state.getRaw(GROUP_ID_1));
		msg1.put(NAME, state.getString(CONTACT_2));
		msg1.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());
		final BdfDictionary msg1send = (BdfDictionary) msg1.clone();
		msg1send.put(MESSAGE_TIME, time);

		final BdfDictionary msg2 = new BdfDictionary();
		msg2.put(TYPE, TYPE_REQUEST);
		msg2.put(SESSION_ID, state.getRaw(SESSION_ID));
		msg2.put(GROUP_ID, state.getRaw(GROUP_ID_2));
		msg2.put(NAME, state.getString(CONTACT_1));
		msg2.put(PUBLIC_KEY, introducee1.getAuthor().getPublicKey());
		final BdfDictionary msg2send = (BdfDictionary) msg2.clone();
		msg2send.put(MESSAGE_TIME, time);

		context.checking(new Expectations() {{
			// initialize and store session state
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
			oneOf(cryptoComponent).getSecureRandom();
			will(returnValue(secureRandom));
			oneOf(secureRandom).nextBytes(salt.getBytes());
			oneOf(introductionGroupFactory).createLocalGroup();
			will(returnValue(localGroup0));
			oneOf(clientHelper).createMessage(localGroup0.getId(), time,
					BdfList.of(salt));
			will(returnValue(msg));
			oneOf(introductionGroupFactory)
					.createIntroductionGroup(introducee1);
			will(returnValue(introductionGroup1));
			oneOf(introductionGroupFactory)
					.createIntroductionGroup(introducee2);
			will(returnValue(introductionGroup2));
			oneOf(clientHelper).addLocalMessage(txn, msg, getClientId(), state,
					false);

			// send message
			oneOf(clientHelper).mergeMessageMetadata(txn, msg.getId(), state2);
			oneOf(messageSender).sendMessage(txn, msg1send);
			oneOf(messageSender).sendMessage(txn, msg2send);
		}});

		introducerManager
				.makeIntroduction(txn, introducee1, introducee2, null, time);

		context.assertIsSatisfied();

		assertFalse(txn.isComplete());
	}

	private ClientId getClientId() {
		return IntroductionManagerImpl.CLIENT_ID;
	}

}
