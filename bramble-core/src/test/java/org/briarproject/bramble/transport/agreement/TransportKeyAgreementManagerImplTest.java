package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.Bytes.compare;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_DO_NOT_SHARE;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.DEFER;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.REJECT;
import static org.briarproject.bramble.api.transport.agreement.TransportKeyAgreementManager.CLIENT_ID;
import static org.briarproject.bramble.api.transport.agreement.TransportKeyAgreementManager.MAJOR_VERSION;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.transport.agreement.MessageType.ACTIVATE;
import static org.briarproject.bramble.transport.agreement.MessageType.KEY;
import static org.briarproject.bramble.transport.agreement.State.ACTIVATED;
import static org.briarproject.bramble.transport.agreement.State.AWAIT_ACTIVATE;
import static org.briarproject.bramble.transport.agreement.State.AWAIT_KEY;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.MSG_KEY_PUBLIC_KEY;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.MSG_KEY_TRANSPORT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TransportKeyAgreementManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final ClientVersioningManager clientVersioningManager =
			context.mock(ClientVersioningManager.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final KeyManager keyManager = context.mock(KeyManager.class);
	private final MessageEncoder messageEncoder =
			context.mock(MessageEncoder.class);
	private final SessionEncoder sessionEncoder =
			context.mock(SessionEncoder.class);
	private final SessionParser sessionParser =
			context.mock(SessionParser.class);
	private final TransportKeyAgreementCrypto crypto =
			context.mock(TransportKeyAgreementCrypto.class);
	private final PluginConfig pluginConfig = context.mock(PluginConfig.class);
	private final SimplexPluginFactory simplexFactory =
			context.mock(SimplexPluginFactory.class);
	private final DuplexPluginFactory duplexFactory =
			context.mock(DuplexPluginFactory.class);

	private final TransportId simplexTransportId = getTransportId();
	private final TransportId duplexTransportId = getTransportId();
	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Contact contact = getContact();
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final boolean alice = compare(localAuthor.getId().getBytes(),
			contact.getAuthor().getId().getBytes()) < 0;
	private final KeyPair localKeyPair =
			new KeyPair(getAgreementPublicKey(), getAgreementPrivateKey());
	private final PublicKey remotePublicKey = getAgreementPublicKey();
	private final SecretKey rootKey = getSecretKey();
	private final KeySetId keySetId = new KeySetId(123);

	private final Message storageMessage = getMessage(contactGroup.getId());
	private final Message localKeyMessage = getMessage(contactGroup.getId());
	private final Message localActivateMessage =
			getMessage(contactGroup.getId());
	private final Message remoteKeyMessage = getMessage(contactGroup.getId());
	private final Message remoteActivateMessage =
			getMessage(contactGroup.getId());
	private final long localTimestamp = localKeyMessage.getTimestamp();
	private final long remoteTimestamp = remoteKeyMessage.getTimestamp();

	// These query and metadata dictionaries are handled by the manager without
	// inspecting their contents, so we can use empty dictionaries for testing
	private final BdfDictionary sessionQuery = new BdfDictionary();
	private final BdfDictionary sessionMeta = new BdfDictionary();
	private final BdfDictionary localKeyMeta = new BdfDictionary();
	private final BdfDictionary localActivateMeta = new BdfDictionary();

	// The manager doesn't use the incoming message body, so it can be empty
	private final BdfList remoteMessageBody = new BdfList();

	private final BdfDictionary remoteKeyMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_MESSAGE_TYPE, KEY.getValue()),
			new BdfEntry(MSG_KEY_TRANSPORT_ID,
					simplexTransportId.getString()),
			new BdfEntry(MSG_KEY_PUBLIC_KEY, remotePublicKey.getEncoded()));

	private final BdfDictionary remoteActivateMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_MESSAGE_TYPE, ACTIVATE.getValue()),
			new BdfEntry(MSG_KEY_TRANSPORT_ID,
					simplexTransportId.getString()));

	private TransportKeyAgreementManagerImpl manager;

	@Before
	public void setUp() {
		context.checking(new Expectations() {{
			oneOf(pluginConfig).getSimplexFactories();
			will(returnValue(singletonList(simplexFactory)));
			oneOf(simplexFactory).getId();
			will(returnValue(simplexTransportId));
			oneOf(pluginConfig).getDuplexFactories();
			will(returnValue(singletonList(duplexFactory)));
			oneOf(duplexFactory).getId();
			will(returnValue(duplexTransportId));
			oneOf(contactGroupFactory)
					.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
			will(returnValue(localGroup));
		}});

		manager = new TransportKeyAgreementManagerImpl(db, clientHelper,
				metadataParser, contactGroupFactory, clientVersioningManager,
				identityManager, keyManager, messageEncoder, sessionEncoder,
				sessionParser, crypto, pluginConfig);
	}

	@Test
	public void testCreatesContactGroupAtStartupIfLocalGroupDoesNotExist()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			// The local group doesn't exist so we need to create contact groups
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			// Create the contact group and set it up
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientHelper)
					.setContactId(txn, contactGroup.getId(), contact.getId());
			oneOf(clientVersioningManager).getClientVisibility(txn,
					contact.getId(), CLIENT_ID, MAJOR_VERSION);
			will(returnValue(VISIBLE));
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), VISIBLE);
			// We already have keys for both transports
			oneOf(db).getTransportsWithKeys(txn);
			will(returnValue(singletonMap(contact.getId(),
					asList(simplexTransportId, duplexTransportId))));
		}});

		manager.onDatabaseOpened(txn);
	}

	@Test
	public void testDoesNotCreateContactGroupAtStartupIfLocalGroupExists()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			// The local group exists so we don't need to create contact groups
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
			// We already have keys for both transports
			oneOf(db).getTransportsWithKeys(txn);
			will(returnValue(singletonMap(contact.getId(),
					asList(simplexTransportId, duplexTransportId))));
		}});

		manager.onDatabaseOpened(txn);
	}

	@Test
	public void testStartsSessionAtStartup() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			// The local group exists so we don't need to create contact groups
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
			// We need keys for the simplex transport
			oneOf(db).getTransportsWithKeys(txn);
			will(returnValue(singletonMap(contact.getId(),
					singletonList(duplexTransportId))));
			// Get the contact group ID
			oneOf(contactGroupFactory)
					.createContactGroup(CLIENT_ID, MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
		}});

		// Check whether a session exists - it doesn't
		expectSessionDoesNotExist(txn);
		// Generate the local key pair
		expectGenerateLocalKeyPair();
		// Send a key message
		expectSendKeyMessage(txn);
		// Save the session
		expectCreateStorageMessage(txn);
		AtomicReference<Session> savedSession = expectSaveSession(txn);

		manager.onDatabaseOpened(txn);

		assertEquals(AWAIT_KEY, savedSession.get().getState());
		assertEquals(localKeyMessage.getId(),
				savedSession.get().getLastLocalMessageId());
		assertEquals(localKeyPair, savedSession.get().getLocalKeyPair());
		assertEquals(Long.valueOf(localTimestamp),
				savedSession.get().getLocalTimestamp());
		assertNull(savedSession.get().getKeySetId());
	}

	@Test
	public void testDefersMessageIfTransportIsNotSupported() throws Exception {
		Transaction txn = new Transaction(null, false);
		TransportId unknownTransportId = getTransportId();
		BdfDictionary meta = new BdfDictionary(remoteKeyMeta);
		meta.put(MSG_KEY_TRANSPORT_ID, unknownTransportId.getString());

		assertEquals(DEFER, manager.incomingMessage(txn, remoteKeyMessage,
				remoteMessageBody, meta));
	}

	@Test
	public void testAcceptsKeyMessageInAwaitKeyState() throws Exception {
		Transaction txn = new Transaction(null, false);
		Session loadedSession = new Session(AWAIT_KEY,
				localKeyMessage.getId(), localKeyPair, localTimestamp, null);

		// Check whether a session exists - it does
		expectLoadSession(txn, loadedSession);
		// Load the contact ID
		expectLoadContactId(txn);
		// Check whether we already have keys - we don't
		expectKeysExist(txn, false);
		// Parse the remote public key
		expectParseRemotePublicKey();
		// Derive and store the transport keys
		expectDeriveAndStoreTransportKeys(txn);
		// Send an activate message
		expectSendActivateMessage(txn);
		// Save the session
		AtomicReference<Session> savedSession = expectSaveSession(txn);

		assertEquals(ACCEPT_DO_NOT_SHARE, manager.incomingMessage(txn,
				remoteKeyMessage, remoteMessageBody, remoteKeyMeta));

		assertEquals(AWAIT_ACTIVATE, savedSession.get().getState());
		assertEquals(localActivateMessage.getId(),
				savedSession.get().getLastLocalMessageId());
		assertNull(savedSession.get().getLocalKeyPair());
		assertNull(savedSession.get().getLocalTimestamp());
		assertEquals(keySetId, savedSession.get().getKeySetId());
	}

	@Test
	public void testAcceptsKeyMessageIfWeHaveTransportKeysButNoSession()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		// Check whether a session exists - it doesn't
		expectSessionDoesNotExist(txn);
		// Load the contact ID
		expectLoadContactId(txn);
		// Check whether we already have keys - we do
		expectKeysExist(txn, true);
		// Generate the local key pair
		expectGenerateLocalKeyPair();
		// Parse the remote public key
		expectParseRemotePublicKey();
		// Send a key message
		expectSendKeyMessage(txn);
		// Derive and store the transport keys
		expectDeriveAndStoreTransportKeys(txn);
		// Send an activate message
		expectSendActivateMessage(txn);
		// Save the session
		expectCreateStorageMessage(txn);
		AtomicReference<Session> savedSession = expectSaveSession(txn);

		assertEquals(ACCEPT_DO_NOT_SHARE, manager.incomingMessage(txn,
				remoteKeyMessage, remoteMessageBody, remoteKeyMeta));

		assertEquals(AWAIT_ACTIVATE, savedSession.get().getState());
		assertEquals(localActivateMessage.getId(),
				savedSession.get().getLastLocalMessageId());
		assertNull(savedSession.get().getLocalKeyPair());
		assertNull(savedSession.get().getLocalTimestamp());
		assertEquals(keySetId, savedSession.get().getKeySetId());
	}

	@Test
	public void testRejectsKeyMessageInAwaitActivateState() throws Exception {
		Session loadedSession = new Session(AWAIT_ACTIVATE,
				localActivateMessage.getId(), null, null, keySetId);
		testRejectsKeyMessageWithExistingSession(loadedSession);
	}

	@Test
	public void testRejectsKeyMessageInActivatedState() throws Exception {
		Session loadedSession = new Session(ACTIVATED,
				localActivateMessage.getId(), null, null, null);
		testRejectsKeyMessageWithExistingSession(loadedSession);
	}

	private void testRejectsKeyMessageWithExistingSession(Session loadedSession)
			throws Exception {
		Transaction txn = new Transaction(null, false);

		// Check whether a session exists - it does
		expectLoadSession(txn, loadedSession);
		// Load the contact ID
		expectLoadContactId(txn);
		// Check whether we already have keys - we don't
		expectKeysExist(txn, false);

		assertEquals(REJECT, manager.incomingMessage(txn,
				remoteKeyMessage, remoteMessageBody, remoteKeyMeta));
	}

	@Test
	public void testAcceptsActivateMessageInAwaitActivateState()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Session loadedSession = new Session(AWAIT_ACTIVATE,
				localActivateMessage.getId(), null, null, keySetId);

		// Check whether a session exists - it does
		expectLoadSession(txn, loadedSession);

		// Activate the transport keys
		context.checking(new Expectations() {{
			oneOf(keyManager).activateKeys(txn,
					singletonMap(simplexTransportId, keySetId));
		}});

		// Save the session
		AtomicReference<Session> savedSession = expectSaveSession(txn);

		assertEquals(ACCEPT_DO_NOT_SHARE, manager.incomingMessage(txn,
				remoteActivateMessage, remoteMessageBody, remoteActivateMeta));

		assertEquals(ACTIVATED, savedSession.get().getState());
		assertEquals(localActivateMessage.getId(),
				savedSession.get().getLastLocalMessageId());
		assertNull(savedSession.get().getLocalKeyPair());
		assertNull(savedSession.get().getLocalTimestamp());
		assertNull(savedSession.get().getKeySetId());
	}

	@Test
	public void testRejectsActivateMessageWithNoSession() throws Exception {
		Transaction txn = new Transaction(null, false);

		// Check whether a session exists - it doesn't
		expectSessionDoesNotExist(txn);

		assertEquals(REJECT, manager.incomingMessage(txn,
				remoteActivateMessage, remoteMessageBody, remoteActivateMeta));
	}

	@Test
	public void testRejectsActivateMessageInAwaitKeyState() throws Exception {
		Session loadedSession = new Session(AWAIT_KEY,
				localKeyMessage.getId(), localKeyPair, localTimestamp, null);
		testRejectsActivateMessageWithExistingSession(loadedSession);
	}

	@Test
	public void testRejectsActivateMessageInActivatedState() throws Exception {
		Session loadedSession = new Session(ACTIVATED,
				localActivateMessage.getId(), null, null, null);
		testRejectsActivateMessageWithExistingSession(loadedSession);
	}

	private void testRejectsActivateMessageWithExistingSession(
			Session loadedSession) throws Exception {
		Transaction txn = new Transaction(null, false);

		// Check whether a session exists - it does
		expectLoadSession(txn, loadedSession);

		assertEquals(REJECT, manager.incomingMessage(txn,
				remoteActivateMessage, remoteMessageBody, remoteActivateMeta));
	}

	private void expectSessionDoesNotExist(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(sessionEncoder).getSessionQuery(simplexTransportId);
			will(returnValue(sessionQuery));
			oneOf(clientHelper)
					.getMessageIds(txn, contactGroup.getId(), sessionQuery);
			will(returnValue(emptyList()));
		}});
	}

	private void expectLoadSession(Transaction txn, Session loadedSession)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(sessionEncoder).getSessionQuery(simplexTransportId);
			will(returnValue(sessionQuery));
			oneOf(clientHelper)
					.getMessageIds(txn, contactGroup.getId(), sessionQuery);
			will(returnValue(singletonList(storageMessage.getId())));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					storageMessage.getId());
			will(returnValue(sessionMeta));
			oneOf(sessionParser).parseSession(sessionMeta);
			will(returnValue(loadedSession));
		}});
	}

	private void expectSendKeyMessage(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeKeyMessage(contactGroup.getId(),
					simplexTransportId, localKeyPair.getPublic());
			will(returnValue(localKeyMessage));
			oneOf(messageEncoder)
					.encodeMessageMetadata(simplexTransportId, KEY, true);
			will(returnValue(localKeyMeta));
			oneOf(clientHelper).addLocalMessage(txn, localKeyMessage,
					localKeyMeta, true, false);
		}});
	}

	private void expectSendActivateMessage(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeActivateMessage(contactGroup.getId(),
					simplexTransportId, localKeyMessage.getId());
			will(returnValue(localActivateMessage));
			oneOf(messageEncoder)
					.encodeMessageMetadata(simplexTransportId, ACTIVATE, true);
			will(returnValue(localActivateMeta));
			oneOf(clientHelper).addLocalMessage(txn, localActivateMessage,
					localActivateMeta, true, false);
		}});
	}

	private void expectCreateStorageMessage(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper)
					.createMessageForStoringMetadata(contactGroup.getId());
			will(returnValue(storageMessage));
			oneOf(db).addLocalMessage(txn, storageMessage, new Metadata(),
					false, false);
		}});
	}

	private AtomicReference<Session> expectSaveSession(Transaction txn)
			throws Exception {
		AtomicReference<Session> savedSession = new AtomicReference<>();

		context.checking(new Expectations() {{
			oneOf(sessionEncoder).encodeSession(with(any(Session.class)),
					with(simplexTransportId));
			will(doAll(
					new CaptureArgumentAction<>(savedSession, Session.class, 0),
					returnValue(sessionMeta)));
			oneOf(clientHelper).mergeMessageMetadata(txn,
					storageMessage.getId(), sessionMeta);
		}});

		return savedSession;
	}

	private void expectLoadContactId(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).getContactId(txn, contactGroup.getId());
			will(returnValue(contact.getId()));
		}});
	}

	private void expectGenerateLocalKeyPair() {
		context.checking(new Expectations() {{
			oneOf(crypto).generateKeyPair();
			will(returnValue(localKeyPair));
		}});
	}

	private void expectParseRemotePublicKey() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).parsePublicKey(remotePublicKey.getEncoded());
			will(returnValue(remotePublicKey));
		}});
	}

	private void expectDeriveAndStoreTransportKeys(Transaction txn)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).deriveRootKey(localKeyPair, remotePublicKey);
			will(returnValue(rootKey));
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(keyManager).addRotationKeys(txn, contact.getId(),
					simplexTransportId, rootKey,
					min(localTimestamp, remoteTimestamp), alice, false);
			will(returnValue(keySetId));
		}});
	}

	private void expectKeysExist(Transaction txn, boolean exist)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).containsTransportKeys(txn, contact.getId(),
					simplexTransportId);
			will(returnValue(exist));
		}});
	}
}
