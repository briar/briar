package org.briarproject.introduction;

import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.SecretKey;
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
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.ADDED_CONTACT_ID;
import static org.briarproject.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PRIVATE_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
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
	private final IntroductionGroupFactory introductionGroupFactory;

	@Inject
	IntroduceeManager(MessageSender messageSender, DatabaseComponent db,
			ClientHelper clientHelper, Clock clock,
			CryptoComponent cryptoComponent,
			TransportPropertyManager transportPropertyManager,
			AuthorFactory authorFactory, ContactManager contactManager,
			IntroductionGroupFactory introductionGroupFactory) {

		this.messageSender = messageSender;
		this.db = db;
		this.clientHelper = clientHelper;
		this.clock = clock;
		this.cryptoComponent = cryptoComponent;
		this.transportPropertyManager = transportPropertyManager;
		this.authorFactory = authorFactory;
		this.contactManager = contactManager;
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
		if(remoteAuthorId.equals(introducer.getLocalAuthorId())) {
			LOG.warning("Received Introduction Request to Ourselves");
			throw new FormatException();
		}

		// check if remote author is actually one of our other identities
		boolean introducesOtherIdentity =
				db.containsLocalAuthor(txn, remoteAuthorId);
		d.put(REMOTE_AUTHOR_IS_US, introducesOtherIdentity);

		// save local state to database
		clientHelper.addLocalMessage(txn, localMsg,
				IntroductionManagerImpl.CLIENT_ID, d, false);

		return d;
	}

	public void incomingMessage(Transaction txn, BdfDictionary state,
			BdfDictionary message) throws DbException, FormatException {

		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, message, engine.onMessageReceived(state, message));
	}

	public void acceptIntroduction(Transaction txn, BdfDictionary state,
			final long timestamp)
			throws DbException, FormatException {

		// get data to connect and derive a shared secret later
		long now = clock.currentTimeMillis();
		KeyPair keyPair = cryptoComponent.generateAgreementKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		Map<TransportId, TransportProperties> transportProperties =
				transportPropertyManager.getLocalProperties(txn);

		// update session state for later
		state.put(ACCEPT, true);
		state.put(OUR_TIME, now);
		state.put(OUR_PUBLIC_KEY, publicKey);
		state.put(OUR_PRIVATE_KEY, privateKey);

		// define action
		BdfDictionary localAction = new BdfDictionary();
		localAction.put(TYPE, TYPE_RESPONSE);
		localAction.put(TRANSPORT,
				encodeTransportProperties(transportProperties));
		localAction.put(MESSAGE_TIME, timestamp);

		// start engine and process its state update
		IntroduceeEngine engine = new IntroduceeEngine();
		processStateUpdate(txn, null, engine.onLocalAction(state, localAction));
	}

	public void declineIntroduction(Transaction txn, BdfDictionary state,
			final long timestamp)
			throws DbException, FormatException {

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

	private void processStateUpdate(Transaction txn, BdfDictionary msg,
			IntroduceeEngine.StateUpdate<BdfDictionary, BdfDictionary>
					result) throws DbException, FormatException {

		// perform actions based on new local state
		performTasks(txn, result.localState);

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
	}

	private void performTasks(Transaction txn, BdfDictionary localState)
			throws FormatException, DbException {

		if (!localState.containsKey(TASK)) return;

		// remember task and remove it from localState
		long task = localState.getLong(TASK);
		localState.put(TASK, BdfDictionary.NULL_VALUE);

		if (task == TASK_ADD_CONTACT) {
			if (localState.getBoolean(EXISTS)) {
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
				publicKeyBytes = localState.getRaw(OUR_PUBLIC_KEY);
				publicKey = keyParser
						.parsePublicKey(publicKeyBytes);
				privateKey = keyParser.parsePrivateKey(
						localState.getRaw(OUR_PRIVATE_KEY));
			} catch (GeneralSecurityException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.log(WARNING, e.toString(), e);
				}
				// we can not continue without the keys
				throw new RuntimeException("Our own ephemeral key is invalid");
			}
			KeyPair keyPair = new KeyPair(publicKey, privateKey);
			byte[] theirEphemeralKey = localState.getRaw(E_PUBLIC_KEY);

			// figure out who takes which role by comparing public keys
			int comp = Bytes.COMPARATOR.compare(new Bytes(publicKeyBytes),
					new Bytes(theirEphemeralKey));
			boolean alice = comp < 0;

			// The master secret is derived from the local ephemeral key pair
			// and the remote ephemeral public key
			SecretKey secretKey;
			try {
				secretKey = cryptoComponent
						.deriveMasterSecret(theirEphemeralKey, keyPair, alice);
			} catch (GeneralSecurityException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
				// we can not continue without the shared secret
				throw new FormatException();
			}

			// The agreed timestamp is the minimum of the peers' timestamps
			long ourTime = localState.getLong(OUR_TIME);
			long theirTime = localState.getLong(TIME);
			long timestamp = Math.min(ourTime, theirTime);

			// Add the contact to the database
			AuthorId localAuthorId =
					new AuthorId(localState.getRaw(LOCAL_AUTHOR_ID));
			Author remoteAuthor = authorFactory
					.createAuthor(localState.getString(NAME),
							localState.getRaw(PUBLIC_KEY));
			ContactId contactId = contactManager
					.addContact(txn, remoteAuthor, localAuthorId, secretKey,
							timestamp, alice, false);

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
			localState.put(OUR_PRIVATE_KEY, BdfDictionary.NULL_VALUE);
		}

		// we sent and received an ACK, so activate contact
		if (task == TASK_ACTIVATE_CONTACT) {
			if (!localState.getBoolean(EXISTS) &&
					localState.containsKey(ADDED_CONTACT_ID)) {

				LOG.info("Activating Contact...");

				ContactId contactId = new ContactId(
						localState.getLong(ADDED_CONTACT_ID).intValue());

				// activate and show contact in contact list
				db.setContactActive(txn, contactId, true);

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
				contactManager.removeContact(contactId);
			}
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
