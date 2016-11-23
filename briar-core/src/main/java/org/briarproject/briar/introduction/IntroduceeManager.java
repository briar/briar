package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ADDED_CONTACT_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.briar.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_PRIVATE_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;

@Immutable
@NotNullByDefault
class IntroduceeManager {

	private static final Logger LOG =
			Logger.getLogger(IntroduceeManager.class.getName());

	static final String SIGNING_LABEL_RESPONSE = CLIENT_ID + "/RESPONSE";

	private final MessageSender messageSender;
	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final Clock clock;
	private final CryptoComponent cryptoComponent;
	private final TransportPropertyManager transportPropertyManager;
	private final AuthorFactory authorFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final IntroductionGroupFactory introductionGroupFactory;

	@Inject
	IntroduceeManager(MessageSender messageSender, DatabaseComponent db,
			ClientHelper clientHelper, Clock clock,
			CryptoComponent cryptoComponent,
			TransportPropertyManager transportPropertyManager,
			AuthorFactory authorFactory, ContactManager contactManager,
			IdentityManager identityManager,
			IntroductionGroupFactory introductionGroupFactory) {

		this.messageSender = messageSender;
		this.db = db;
		this.clientHelper = clientHelper;
		this.clock = clock;
		this.cryptoComponent = cryptoComponent;
		this.transportPropertyManager = transportPropertyManager;
		this.authorFactory = authorFactory;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.introductionGroupFactory = introductionGroupFactory;
	}

	public BdfDictionary initialize(Transaction txn, GroupId groupId,
			BdfDictionary message) throws DbException, FormatException {

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes salt = new Bytes(new byte[64]);
		cryptoComponent.getSecureRandom().nextBytes(salt.getBytes());

		Message localMsg = clientHelper.createMessage(
				introductionGroupFactory.createLocalGroup().getId(), now,
				BdfList.of(salt));
		MessageId storageId = localMsg.getId();

		// find out who is introducing us
		BdfDictionary gd =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		ContactId introducerId =
				new ContactId(gd.getLong(CONTACT).intValue());
		Contact introducer = db.getContact(txn, introducerId);

		BdfDictionary d = new BdfDictionary();
		d.put(STORAGE_ID, storageId);
		d.put(STATE, AWAIT_REQUEST.getValue());
		d.put(ROLE, ROLE_INTRODUCEE);
		d.put(GROUP_ID, groupId);
		d.put(INTRODUCER, introducer.getAuthor().getName());
		d.put(CONTACT_ID_1, introducer.getId().getInt());
		d.put(LOCAL_AUTHOR_ID, introducer.getLocalAuthorId().getBytes());
		d.put(NOT_OUR_RESPONSE, storageId);
		d.put(ANSWERED, false);

		// check if the contact we are introduced to does already exist
		AuthorId remoteAuthorId = authorFactory
				.createAuthor(message.getString(NAME),
						message.getRaw(PUBLIC_KEY)).getId();
		boolean exists = contactManager.contactExists(txn, remoteAuthorId,
				introducer.getLocalAuthorId());
		d.put(EXISTS, exists);
		d.put(REMOTE_AUTHOR_ID, remoteAuthorId);

		// check if someone is trying to introduce us to ourselves
		if (remoteAuthorId.equals(introducer.getLocalAuthorId())) {
			LOG.warning("Received Introduction Request to Ourselves");
			throw new FormatException();
		}

		// check if remote author is actually one of our other identities
		boolean introducesOtherIdentity =
				db.containsLocalAuthor(txn, remoteAuthorId);
		d.put(REMOTE_AUTHOR_IS_US, introducesOtherIdentity);

		// save local state to database
		clientHelper.addLocalMessage(txn, localMsg, d, false);

		return d;
	}

	public void incomingMessage(Transaction txn, BdfDictionary state,
			BdfDictionary message) throws DbException, FormatException {

		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, message,
				engine.onMessageReceived(state, message));
	}

	void acceptIntroduction(Transaction txn, BdfDictionary state,
			long timestamp) throws DbException, FormatException {

		// get data to connect and derive a shared secret later
		long now = clock.currentTimeMillis();
		KeyPair keyPair = cryptoComponent.generateAgreementKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		Map<TransportId, TransportProperties> transportProperties =
				transportPropertyManager.getLocalProperties(txn);
		BdfDictionary tp = encodeTransportProperties(transportProperties);

		// update session state for later
		state.put(ACCEPT, true);
		state.put(OUR_TIME, now);
		state.put(OUR_PUBLIC_KEY, publicKey);
		state.put(OUR_PRIVATE_KEY, privateKey);
		state.put(OUR_TRANSPORT, tp);

		// define action
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_RESPONSE);
		localAction.put(TRANSPORT, tp);
		localAction.put(MESSAGE_TIME, timestamp);

		// start engine and process its state update
		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, null, engine.onLocalAction(state, localAction));
	}

	void declineIntroduction(Transaction txn, BdfDictionary state,
			long timestamp) throws DbException, FormatException {

		// update session state
		state.put(ACCEPT, false);

		// define action
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_RESPONSE);
		localAction.put(MESSAGE_TIME, timestamp);

		// start engine and process its state update
		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, null,
				engine.onLocalAction(state, localAction));
	}

	private void processStateUpdate(Transaction txn,
			@Nullable BdfDictionary msg,
			IntroduceeEngine.StateUpdate<BdfDictionary, BdfDictionary> result)
			throws DbException, FormatException {

		// perform actions based on new local state
		BdfDictionary followUpAction = performTasks(txn, result.localState);

		// save new local state
		MessageId storageId =
				new MessageId(result.localState.getRaw(STORAGE_ID));
		clientHelper.mergeMessageMetadata(txn, storageId, result.localState);

		// send messages
		for (BdfDictionary d : result.toSend) {
			messageSender.sendMessage(txn, d);
		}

		// broadcast events
		for (Event event : result.toBroadcast) {
			txn.attach(event);
		}

		// delete message
		if (result.deleteMessage && msg != null) {
			MessageId messageId = new MessageId(msg.getRaw(MESSAGE_ID));
			if (LOG.isLoggable(INFO)) {
				LOG.info("Deleting message with id " + messageId.hashCode());
			}
			db.deleteMessage(txn, messageId);
			db.deleteMessageMetadata(txn, messageId);
		}

		// process follow up action at the end if available
		if (followUpAction != null) {
			IntroduceeEngine engine = new IntroduceeEngine();
			processStateUpdate(txn, null,
					engine.onLocalAction(result.localState, followUpAction));
		}
	}

	@Nullable
	private BdfDictionary performTasks(Transaction txn,
			BdfDictionary localState)
			throws FormatException, DbException {

		if (!localState.containsKey(TASK) || localState.get(TASK) == NULL_VALUE)
			return null;

		// remember task and remove it from localState
		long task = localState.getLong(TASK);
		localState.put(TASK, NULL_VALUE);

		if (task == TASK_ADD_CONTACT) {
			if (localState.getBoolean(EXISTS)) {
				// we have this contact already, so do not perform actions
				LOG.info("We have this contact already, do not add");
				return null;
			}

			// figure out who takes which role by comparing public keys
			byte[] publicKeyBytes = localState.getRaw(OUR_PUBLIC_KEY);
			byte[] theirEphemeralKey = localState.getRaw(E_PUBLIC_KEY);
			int comp = Bytes.COMPARATOR.compare(new Bytes(publicKeyBytes),
					new Bytes(theirEphemeralKey));
			boolean alice = comp < 0;

			// get our local author
			LocalAuthor author = identityManager.getLocalAuthor(txn);

			SecretKey secretKey;
			byte[] privateKeyBytes = localState.getRaw(OUR_PRIVATE_KEY);
			try {
				// derive secret master key
				secretKey =
						deriveSecretKey(publicKeyBytes, privateKeyBytes, alice,
								theirEphemeralKey);
				// derive MAC keys and nonces, sign our nonce and calculate MAC
				deriveMacKeysAndNonces(localState, author, secretKey, alice);
			} catch (GeneralSecurityException e) {
				// we can not continue without the signature
				throw new DbException(e);
			}

			LOG.info("Adding contact in inactive state");

			// The agreed timestamp is the minimum of the peers' timestamps
			long ourTime = localState.getLong(OUR_TIME);
			long theirTime = localState.getLong(TIME);
			long timestamp = Math.min(ourTime, theirTime);

			// Add the contact to the database as inactive
			Author remoteAuthor = authorFactory
					.createAuthor(localState.getString(NAME),
							localState.getRaw(PUBLIC_KEY));
			ContactId contactId = contactManager
					.addContact(txn, remoteAuthor, author.getId(), secretKey,
							timestamp, alice, false, false);

			// Update local state with ContactId, so we know what to activate
			localState.put(ADDED_CONTACT_ID, contactId.getInt());

			// let the transport manager know how to connect to the contact
			Map<TransportId, TransportProperties> transportProperties =
					parseTransportProperties(localState);
			transportPropertyManager.addRemoteProperties(txn, contactId,
					transportProperties);

			// delete the ephemeral private key by overwriting with NULL value
			// this ensures future ephemeral keys can not be recovered when
			// this device should gets compromised
			localState.put(OUR_PRIVATE_KEY, NULL_VALUE);

			// define next action: Send ACK
			BdfDictionary localAction = new BdfDictionary();
			localAction.put(TYPE, TYPE_ACK);

			// return follow up action to start engine
			// and process its state update again
			return localAction;
		}

		// we sent and received an ACK, so activate contact
		if (task == TASK_ACTIVATE_CONTACT) {
			if (!localState.getBoolean(EXISTS) &&
					localState.containsKey(ADDED_CONTACT_ID)) {
				try {
					LOG.info("Verifying Signature...");
					verifySignature(localState);
					LOG.info("Verifying MAC...");
					verifyMac(localState);
				} catch (GeneralSecurityException e) {
					throw new DbException(e);
				}

				LOG.info("Activating Contact...");

				ContactId contactId = new ContactId(
						localState.getLong(ADDED_CONTACT_ID).intValue());

				// activate and show contact in contact list
				contactManager.setContactActive(txn, contactId, true);

				// broadcast event informing of successful introduction
				Contact contact = db.getContact(txn, contactId);
				Event event = new IntroductionSucceededEvent(contact);
				txn.attach(event);
			} else {
				LOG.info(
						"We must have had this contact already, not activating...");
			}
		}

		// we need to abort the protocol, clean up what has been done
		if (task == TASK_ABORT) {
			if (localState.containsKey(ADDED_CONTACT_ID)) {
				LOG.info("Deleting added contact due to abort...");
				ContactId contactId = new ContactId(
						localState.getLong(ADDED_CONTACT_ID).intValue());
				contactManager.removeContact(txn, contactId);
			}
		}
		return null;
	}

	private SecretKey deriveSecretKey(byte[] publicKeyBytes,
			byte[] privateKeyBytes, boolean alice, byte[] theirPublicKey)
			throws GeneralSecurityException {
		// parse the local ephemeral key pair
		KeyParser keyParser = cryptoComponent.getAgreementKeyParser();
		PublicKey publicKey;
		PrivateKey privateKey;
		try {
			publicKey = keyParser.parsePublicKey(publicKeyBytes);
			privateKey = keyParser.parsePrivateKey(privateKeyBytes);
		} catch (GeneralSecurityException e) {
			if (LOG.isLoggable(WARNING)) {
				LOG.log(WARNING, e.toString(), e);
			}
			throw new RuntimeException("Our own ephemeral key is invalid");
		}
		KeyPair keyPair = new KeyPair(publicKey, privateKey);

		// The master secret is derived from the local ephemeral key pair
		// and the remote ephemeral public key
		return cryptoComponent
				.deriveMasterSecret(theirPublicKey, keyPair, alice);
	}

	/**
	 * Derives nonces, signs our nonce and calculates MAC
	 * <p>
	 * Derives two nonces and two mac keys from the secret master key.
	 * The other introducee's nonce and MAC key are added to the localState.
	 * <p>
	 * Our nonce is signed with the local author's long-term private key.
	 * The signature is added to the localState.
	 * <p>
	 * Calculates a MAC and stores it in the localState.
	 */
	private void deriveMacKeysAndNonces(BdfDictionary localState,
			LocalAuthor author, SecretKey secretKey, boolean alice)
			throws FormatException, GeneralSecurityException {
		// Derive two nonces and a MAC key from the secret master key
		byte[] ourNonce =
				cryptoComponent.deriveSignatureNonce(secretKey, alice);
		byte[] theirNonce =
				cryptoComponent.deriveSignatureNonce(secretKey, !alice);
		SecretKey macKey = cryptoComponent.deriveMacKey(secretKey, alice);
		SecretKey theirMacKey = cryptoComponent.deriveMacKey(secretKey, !alice);

		// Save the other nonce and MAC key for the verification
		localState.put(NONCE, theirNonce);
		localState.put(MAC_KEY, theirMacKey.getBytes());

		// Sign our nonce with our long-term identity public key
		byte[] sig = cryptoComponent
				.sign(SIGNING_LABEL_RESPONSE, ourNonce, author.getPrivateKey());

		// Calculate a MAC over identity public key, ephemeral public key,
		// transport properties and timestamp.
		byte[] publicKeyBytes = localState.getRaw(OUR_PUBLIC_KEY);
		BdfDictionary tp = localState.getDictionary(OUR_TRANSPORT);
		long ourTime = localState.getLong(OUR_TIME);
		BdfList toMacList = BdfList.of(author.getPublicKey(),
				publicKeyBytes, tp, ourTime);
		byte[] toMac = clientHelper.toByteArray(toMacList);
		byte[] mac = cryptoComponent.mac(macKey, toMac);

		// Add MAC and signature to localState, so it can be included in ACK
		localState.put(OUR_MAC, mac);
		localState.put(OUR_SIGNATURE, sig);
	}

	void verifySignature(BdfDictionary localState)
			throws FormatException, GeneralSecurityException {
		byte[] nonce = localState.getRaw(NONCE);
		byte[] sig = localState.getRaw(SIGNATURE);
		byte[] key = localState.getRaw(PUBLIC_KEY);

		// Verify the signature
		if (!cryptoComponent.verify(SIGNING_LABEL_RESPONSE, nonce, key, sig)) {
			LOG.warning("Invalid nonce signature in ACK");
			throw new GeneralSecurityException();
		}
	}

	void verifyMac(BdfDictionary localState)
			throws FormatException, GeneralSecurityException {
		// get MAC and MAC key from session state
		byte[] mac = localState.getRaw(MAC);
		byte[] macKeyBytes = localState.getRaw(MAC_KEY);
		SecretKey macKey = new SecretKey(macKeyBytes);

		// get MAC data and calculate a new MAC with stored key
		byte[] pubKey = localState.getRaw(PUBLIC_KEY);
		byte[] ePubKey = localState.getRaw(E_PUBLIC_KEY);
		BdfDictionary tp = localState.getDictionary(TRANSPORT);
		long timestamp = localState.getLong(TIME);
		BdfList toMacList = BdfList.of(pubKey, ePubKey, tp, timestamp);
		byte[] toMac = clientHelper.toByteArray(toMacList);
		byte[] calculatedMac = cryptoComponent.mac(macKey, toMac);
		if (!Arrays.equals(mac, calculatedMac)) {
			LOG.warning("Received ACK with invalid MAC");
			throw new GeneralSecurityException();
		}
	}

	public void abort(Transaction txn, BdfDictionary state) {
		IntroduceeEngine engine = new IntroduceeEngine();
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_ABORT);
		try {
			processStateUpdate(txn, null,
					engine.onLocalAction(state, localAction));
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private BdfDictionary encodeTransportProperties(
			Map<TransportId, TransportProperties> map) {

		BdfDictionary d = new BdfDictionary();
		for (Map.Entry<TransportId, TransportProperties> e : map.entrySet()) {
			d.put(e.getKey().getString(), e.getValue());
		}
		return d;
	}

	private Map<TransportId, TransportProperties> parseTransportProperties(
			BdfDictionary d) throws FormatException {

		Map<TransportId, TransportProperties> tpMap =
				new HashMap<TransportId, TransportProperties>();
		BdfDictionary tpMapDict = d.getDictionary(TRANSPORT);
		for (String key : tpMapDict.keySet()) {
			TransportId transportId = new TransportId(key);
			TransportProperties transportProperties = new TransportProperties();
			BdfDictionary tpDict = tpMapDict.getDictionary(key);
			for (String tkey : tpDict.keySet()) {
				transportProperties.put(tkey, tpDict.getString(tkey));
			}
			tpMap.put(transportId, transportProperties);
		}
		return tpMap;
	}

}
