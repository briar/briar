package org.briarproject.introduction;

import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.IntroductionSucceededEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_MAC;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PRIVATE_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NO_TASK;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

class IntroduceeManager {

	private static final Logger LOG =
			Logger.getLogger(IntroduceeManager.class.getName());

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

	public IntroduceeSessionState initialize(Transaction txn,
			SessionId sessionId, GroupId groupId, BdfDictionary message)
			throws DbException, FormatException {

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

		IntroduceeSessionState localState = new IntroduceeSessionState(storageId,
				sessionId, groupId, introducer.getId(),
				introducer.getAuthor().getId(), introducer.getAuthor().getName(),
				introducer.getLocalAuthorId(), AWAIT_REQUEST);

		// check if the contact we are introduced to does already exist
		AuthorId remoteAuthorId = authorFactory
				.createAuthor(message.getString(NAME),
						message.getRaw(PUBLIC_KEY)).getId();
		boolean exists = contactManager.contactExists(txn, remoteAuthorId,
				introducer.getLocalAuthorId());
		localState.setContactExists(exists);
		localState.setRemoteAuthorId(remoteAuthorId);
		localState.setLocalAuthorId((introducer.getLocalAuthorId()));
		localState.setName(message.getString(NAME));

		// check if someone is trying to introduce us to ourselves
		if(remoteAuthorId.equals(introducer.getLocalAuthorId())) {
			LOG.warning("Received Introduction Request to Ourselves");
			throw new FormatException();
		}

		// check if remote author is actually one of our other identities
		boolean introducesOtherIdentity =
				db.containsLocalAuthor(txn, remoteAuthorId);
		localState.setRemoteAuthorIsUs(introducesOtherIdentity);

		// save local state to database
		clientHelper.addLocalMessage(txn, localMsg,
				localState.toBdfDictionary(), false);

		return localState;
	}

	public void incomingMessage(Transaction txn, IntroduceeSessionState state,
			BdfDictionary message) throws DbException, FormatException {

		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, message, engine.onMessageReceived(state, message));
	}

	void acceptIntroduction(Transaction txn,
			IntroduceeSessionState state, final long timestamp)
			throws DbException, FormatException {

		// get data to connect and derive a shared secret later
		long now = clock.currentTimeMillis();
		KeyPair keyPair = cryptoComponent.generateAgreementKeyPair();
		Map<TransportId, TransportProperties> transportProperties =
				transportPropertyManager.getLocalProperties(txn);
		BdfDictionary tp = encodeTransportProperties(transportProperties);

		// update session state for later
		state.setAccept(true);
		state.setOurTime(now);
		state.setOurPrivateKey(keyPair.getPrivate().getEncoded());
		state.setOurPublicKey(keyPair.getPublic().getEncoded());
        state.setOurTransport(tp);

		// define action
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_RESPONSE);
		localAction.put(TRANSPORT, tp);
		localAction.put(MESSAGE_TIME, timestamp);

		// start engine and process its state update
		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, null, engine.onLocalAction(state, localAction));
	}

	void declineIntroduction(Transaction txn,
			IntroduceeSessionState state, final long timestamp)
			throws DbException, FormatException {

		// update session state
		state.setAccept(false);

		// define action
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_RESPONSE);
		localAction.put(MESSAGE_TIME, timestamp);

		// start engine and process its state update
		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, null,
				engine.onLocalAction(state, localAction));
	}

	private void processStateUpdate(Transaction txn, BdfDictionary msg,
			IntroduceeEngine.StateUpdate<IntroduceeSessionState, BdfDictionary>
					result) throws DbException, FormatException {

		// perform actions based on new local state
		performTasks(txn, result.localState);

		// save new local state
		MessageId storageId = result.localState.getStorageId();
		clientHelper.mergeMessageMetadata(txn, storageId, 
				result.localState.toBdfDictionary());

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
	}

	private void performTasks(Transaction txn, 
			IntroduceeSessionState localState)
			throws FormatException, DbException {

		long task = localState.getTask();
		if (task == NO_TASK) return;

		// remember task and remove it from localState
		localState.setTask(NO_TASK);

		if (task == TASK_ADD_CONTACT) {
			if (localState.getContactExists()) {
				// we have this contact already, so do not perform actions
				LOG.info("We have this contact already, do not add");
				return;
			}

			LOG.info("Adding contact in inactive state");

			// get all keys
			KeyParser keyParser = cryptoComponent.getAgreementKeyParser();
			byte[] publicKeyBytes;
			PublicKey publicKey;
			PrivateKey privateKey;
			try {
				publicKeyBytes = localState.getOurPublicKey();
				publicKey = keyParser.parsePublicKey(publicKeyBytes);
				privateKey = keyParser.parsePrivateKey(
						localState.getOurPrivateKey());
			} catch (GeneralSecurityException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.log(WARNING, e.toString(), e);
				}
				// we can not continue without the keys
				throw new RuntimeException("Our own ephemeral key is invalid");
			}

			KeyPair ourEphemeralKeyPair;
			ourEphemeralKeyPair = new KeyPair(publicKey, privateKey);
			byte[] theirEphemeralKey = localState.getEPublicKey();

			// figure out who takes which role by comparing public keys
			int comp = new Bytes(publicKeyBytes).compareTo(
					new Bytes(theirEphemeralKey));
			boolean alice = comp < 0;

			// The master secret is derived from the local ephemeral key pair
			// and the remote ephemeral public key
			SecretKey secretKey;
			try {
				secretKey = cryptoComponent
						.deriveMasterSecret(theirEphemeralKey,
								ourEphemeralKeyPair, alice);
			} catch (GeneralSecurityException e) {
				// we can not continue without the shared secret
				throw new DbException(e);
			}

			// Derive two nonces and a MAC key from the secret master key
			byte[] ourNonce =
					cryptoComponent.deriveSignatureNonce(secretKey, alice);
			byte[] theirNonce =
					cryptoComponent.deriveSignatureNonce(secretKey, !alice);
			SecretKey macKey = cryptoComponent.deriveMacKey(secretKey, alice);
			SecretKey theirMacKey =
					cryptoComponent.deriveMacKey(secretKey, !alice);

			// Save the other nonce and MAC key for the verification
			localState.setNonce(theirNonce);
			localState.setMacKey(theirMacKey.getBytes());

			// Sign our nonce with our long-term identity public key
			AuthorId localAuthorId = localState.getLocalAuthorId();
			LocalAuthor author =
					identityManager.getLocalAuthor(txn, localAuthorId);
			Signature signature = cryptoComponent.getSignature();
			KeyParser sigParser = cryptoComponent.getSignatureKeyParser();
			try {
				PrivateKey privKey =
						sigParser.parsePrivateKey(author.getPrivateKey());
				signature.initSign(privKey);
			} catch (GeneralSecurityException e) {
				// we can not continue without the signature
				throw new DbException(e);
			}
			signature.update(ourNonce);
			byte[] sig = signature.sign();


			// The agreed timestamp is the minimum of the peers' timestamps
			long ourTime = localState.getOurTime();
			long theirTime = localState.getTheirTime();
			long timestamp = Math.min(ourTime, theirTime);

			// Calculate a MAC over identity public key, ephemeral public key,
			// transport properties and timestamp.
			BdfDictionary tp = localState.getOurTransport();
			BdfList toSignList = BdfList.of(author.getPublicKey(),
					publicKeyBytes, tp, ourTime);
			byte[] toSign = clientHelper.toByteArray(toSignList);
			byte[] mac = cryptoComponent.mac(macKey, toSign);

			// Add MAC and signature to localState, so it can be included in ACK
			localState.setOurMac(mac);
			localState.setOurSignature(sig);

			// Add the contact to the database as inactive
			Author remoteAuthor = authorFactory
					.createAuthor(localState.getName(),
							localState.getIntroducedPublicKey());
			ContactId contactId = contactManager
					.addContact(txn, remoteAuthor, localAuthorId, secretKey,
							timestamp, alice, false, false);

			// Update local state with ContactId, so we know what to activate
			localState.setIntroducedId(contactId);

			// let the transport manager know how to connect to the contact
			Map<TransportId, TransportProperties> transportProperties =
					parseTransportProperties(localState.toBdfDictionary());
			transportPropertyManager.addRemoteProperties(txn, contactId,
					transportProperties);

			// delete the ephemeral private key by overwriting with NULL value
			// this ensures future ephemeral keys can not be recovered when
			// this device should gets compromised
			localState.clearOurKeyPair();

			// define next action: Send ACK
			BdfDictionary localAction = new BdfDictionary();
			localAction.put(TYPE, TYPE_ACK);

			// start engine and process its state update
			IntroduceeEngine engine = new IntroduceeEngine();
			processStateUpdate(txn, null,
					engine.onLocalAction(localState, localAction));
		}

		// we sent and received an ACK, so activate contact
		if (task == TASK_ACTIVATE_CONTACT) {
			if (!localState.getContactExists() && localState.getIntroducedId() != null) {

				LOG.info("Verifying Signature...");

				byte[] nonce = localState.getNonce();
				byte[] sig = localState.getSignature();
				byte[] keyBytes = localState.getIntroducedPublicKey();
				try {
					// Parse the public key
					KeyParser keyParser = cryptoComponent.getSignatureKeyParser();
					PublicKey key = keyParser.parsePublicKey(keyBytes);
					// Verify the signature
					Signature signature = cryptoComponent.getSignature();
					signature.initVerify(key);
					signature.update(nonce);
					if (!signature.verify(sig)) {
						LOG.warning("Invalid nonce signature in ACK");
						throw new GeneralSecurityException();
					}
				} catch (GeneralSecurityException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					// we can not continue without verifying the signature
					throw new DbException(e);
				}

				LOG.info("Verifying MAC...");

				// get MAC and MAC key from session state
				byte[] mac = localState.getMac();
				byte[] macKeyBytes = localState.getMacKey();
				SecretKey macKey = new SecretKey(macKeyBytes);

				// get MAC data and calculate a new MAC with stored key
				byte[] pubKey = localState.getIntroducedPublicKey();
				byte[] ePubKey = localState.getEPublicKey();
				BdfDictionary tp = localState.getTransport();
				long timestamp = localState.getTheirTime();
				BdfList toSignList = BdfList.of(pubKey, ePubKey, tp, timestamp);
				byte[] toSign = clientHelper.toByteArray(toSignList);
				byte[] calculatedMac = cryptoComponent.mac(macKey, toSign);
				if (!Arrays.equals(mac, calculatedMac)) {
					LOG.warning("Received ACK with invalid MAC");
					throw new DbException();
				}

				LOG.info("Activating Contact...");

				ContactId contactId = localState.getIntroducedId();

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
			if (localState.getIntroducedId() != null) {
				LOG.info("Deleting added contact due to abort...");
				ContactId contactId = localState.getIntroducedId();
				contactManager.removeContact(txn, contactId);
			}
		}

	}

	public void abort(Transaction txn, IntroduceeSessionState state) {

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
