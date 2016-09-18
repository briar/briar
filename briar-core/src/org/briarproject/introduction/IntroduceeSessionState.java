package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroduceeProtocolState;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.ADDED_CONTACT_ID;
import static org.briarproject.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.NO_TASK;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PRIVATE_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_KEY;

// This class is not thread-safe
class IntroduceeSessionState extends IntroductionState {

	private IntroduceeProtocolState state;

	private final ContactId introducerId;
	private final AuthorId introducerAuthorId;
	private final String introducerName;

	private AuthorId localAuthorId;

	private long ourTime;
	private long theirTime;

	private byte[] ourPrivateKey;
	private byte[] ourPublicKey;
	private byte[] introducedPublicKey;
	private byte[] theirEphemeralPublicKey;

	private byte[] mac;
	private byte[] signature;
	private byte[] ourMac;
	private byte[] ourSignature;
	private BdfDictionary ourTransport;
	private byte[] theirNonce;
	private byte[] theirMacKey;

	private int task;

	private String message;
	private BdfDictionary ourTransportProperties;
			// FIXME should not be a dictionary

	private boolean answered;
	private boolean accept;
	private boolean accepted;

	private boolean contactAlreadyExists;

	private byte[] otherResponseId;

	private AuthorId remoteAuthorId;
	private boolean remoteAuthorIsUs;

	private String introducedName;
	private GroupId introductionGroupId;
	private ContactId introducedId;
	//	private String introducedName;
	private AuthorId introducedAuthorId;

	IntroduceeSessionState(MessageId storageId, SessionId sessionId,
			GroupId groupId,
			ContactId introducerId, AuthorId introducerAuthorId,
			String introducerName, AuthorId introducerLocalAuthorId,
			IntroduceeProtocolState state) {

		super(sessionId, storageId);

		this.introducerName = introducerName;
		this.introducerId = introducerId;
		this.introducerAuthorId = introducerAuthorId;
		this.otherResponseId = sessionId.getBytes();
		this.localAuthorId = introducerLocalAuthorId;
		this.state = state;
		this.answered = false;
		this.accept = false;
		this.accepted = false;
		this.contactAlreadyExists = false;
		this.otherResponseId = null;
		this.task = NO_TASK;
		this.ourTransportProperties = null;
		this.introductionGroupId = groupId;

	}

	BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(ROLE, ROLE_INTRODUCEE);
		d.put(STATE, getState().getValue());
		d.put(INTRODUCER, introducerName);
		d.put(ANSWERED, answered);
		d.put(REMOTE_AUTHOR_IS_US, remoteAuthorIsUs);

		if (message != null)
			d.put(MSG, message);

		if (accepted)
			d.put(ACCEPT, accept);

		if (introducedId != null)
			d.put(ADDED_CONTACT_ID, introducedId.getInt());

		d.put(GROUP_ID, introductionGroupId);

		d.put(AUTHOR_ID_1, introducerAuthorId);
		d.put(CONTACT_1, introducerName);
		d.put(CONTACT_ID_1, introducerId.getInt());

		if (introducedAuthorId != null) {
			d.put(AUTHOR_ID_2, introducedAuthorId);
			d.put(CONTACT_ID_2, introducedId);
		}
		// TODO check if we really need three names and what this introducedName refers to
		if (introducedName != null) d.put(NAME, introducedName);

		if (remoteAuthorId != null)
			d.put(REMOTE_AUTHOR_ID, remoteAuthorId);

		d.put(LOCAL_AUTHOR_ID, localAuthorId);

		if (ourTransportProperties != null)
			d.put(TRANSPORT, ourTransportProperties);

		if (ourPublicKey != null)
			d.put(OUR_PUBLIC_KEY, ourPublicKey);

		if (ourPrivateKey != null)
			d.put(OUR_PRIVATE_KEY, ourPrivateKey);
		else
			d.put(OUR_PRIVATE_KEY, BdfDictionary.NULL_VALUE);

		if (theirEphemeralPublicKey != null)
			d.put(E_PUBLIC_KEY, theirEphemeralPublicKey);
		else
			d.put(E_PUBLIC_KEY, BdfDictionary.NULL_VALUE);

		if (introducedPublicKey != null)
			d.put(PUBLIC_KEY, introducedPublicKey);

		if (otherResponseId != null)
			d.put(NOT_OUR_RESPONSE, getOtherResponseId());

		if (mac != null)
			d.put(MAC, mac);

		if (signature != null)
			d.put(SIGNATURE, signature);

		if (ourMac != null)
			d.put(OUR_MAC, ourMac);

		if (ourSignature != null)
			d.put(OUR_SIGNATURE, ourSignature);

		if (ourTransport != null)
			d.put(OUR_TRANSPORT, ourTransport);

		if (theirNonce != null)
			d.put(NONCE, theirNonce);

		if (theirMacKey != null)
			d.put(MAC_KEY, theirMacKey);

		d.put(TIME, theirTime);
		d.put(OUR_TIME, ourTime);
		d.put(EXISTS, contactAlreadyExists);
		d.put(TASK, task);

		return d;
	}

	static IntroduceeSessionState fromBdfDictionary(BdfDictionary d)
			throws FormatException {

		if (d.getLong(ROLE).intValue() != ROLE_INTRODUCEE)
			throw new FormatException();

		MessageId storageId = new MessageId(d.getRaw(STORAGE_ID));
		SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));

		GroupId groupId = new GroupId(d.getRaw(GROUP_ID));

		AuthorId authorId1 = new AuthorId(d.getRaw(AUTHOR_ID_1));
		String introducerName = d.getString(INTRODUCER);
		ContactId introducerId =
				new ContactId(d.getLong(CONTACT_ID_1).intValue());
		AuthorId introducerLocalAuthorId =
				new AuthorId(d.getRaw(LOCAL_AUTHOR_ID));

		int stateNumber = d.getLong(STATE).intValue();
		IntroduceeProtocolState state =
				IntroduceeProtocolState.fromValue(stateNumber);

		IntroduceeSessionState sessionState =
				new IntroduceeSessionState(storageId,
						sessionId, groupId, introducerId, authorId1,
						introducerName, introducerLocalAuthorId, state);

		if (d.containsKey(AUTHOR_ID_2)) {
			sessionState
					.setIntroducedAuthorId(new AuthorId(d.getRaw(AUTHOR_ID_2)));
			sessionState.setIntroducedId(
					new ContactId(d.getLong(CONTACT_ID_2).intValue()));
		}

		if (d.containsKey(REMOTE_AUTHOR_ID))
			sessionState.setRemoteAuthorId(
					new AuthorId(d.getRaw(REMOTE_AUTHOR_ID)));

		if (d.containsKey(TRANSPORT))
			sessionState.setOurTransportProperties(d.getDictionary(TRANSPORT));

		if (d.containsKey(OUR_PUBLIC_KEY))
			sessionState.ourPublicKey = d.getRaw(OUR_PUBLIC_KEY);

		if (d.containsKey(OUR_PRIVATE_KEY)&&
				d.get(OUR_PRIVATE_KEY) != BdfDictionary.NULL_VALUE)
			sessionState.ourPrivateKey = d.getRaw(OUR_PRIVATE_KEY);

		if (d.containsKey(E_PUBLIC_KEY) &&
				d.get(E_PUBLIC_KEY) !=  BdfDictionary.NULL_VALUE)
			sessionState.theirEphemeralPublicKey = d.getRaw(E_PUBLIC_KEY);

		if (d.containsKey(PUBLIC_KEY))
			sessionState.setIntroducedPublicKey(d.getRaw(PUBLIC_KEY));

		if (d.containsKey(ACCEPT))
			sessionState.setAccept(d.getBoolean(ACCEPT));

		if (d.containsKey(NOT_OUR_RESPONSE))
			sessionState.setTheirResponseId(d.getRaw(NOT_OUR_RESPONSE));

		if (d.containsKey(MAC))
			sessionState.setMac(d.getRaw(MAC));

		if (d.containsKey(SIGNATURE))
			sessionState.setSignature(d.getRaw(SIGNATURE));

		if (d.containsKey(OUR_TRANSPORT))
			sessionState.setOurTransport(d.getDictionary(OUR_TRANSPORT));

		sessionState.setTheirTime(d.getLong(TIME));
		sessionState.setOurTime(d.getLong(OUR_TIME));

		if (d.containsKey(NAME))
			sessionState.setIntroducedName(d.getString(NAME));

		sessionState.setContactExists(d.getBoolean(EXISTS));
		sessionState.setTask(d.getLong(TASK).intValue());
		sessionState.setRemoteAuthorIsUs(d.getBoolean(REMOTE_AUTHOR_IS_US));

		if (d.containsKey(ADDED_CONTACT_ID)) {
			ContactId introducedId =
					new ContactId(d.getLong(ADDED_CONTACT_ID).intValue());
			sessionState.setIntroducedId(introducedId);
		}

		if (d.containsKey(ANSWERED))
			sessionState.setAnswered(d.getBoolean(ANSWERED));

		if (d.containsKey(MSG))
			sessionState.setMessage(d.getString(MSG));

		if (d.containsKey(NONCE))
			sessionState.setTheirNonce(d.getRaw(NONCE));

		if (d.containsKey(MAC_KEY))
			sessionState.setTheirMacKey(d.getRaw(MAC_KEY));

		if (d.containsKey(OUR_MAC)) {
			sessionState.setOurMac(d.getRaw(OUR_MAC));
		}

		if (d.containsKey(OUR_SIGNATURE))
			sessionState.setOurSignature(d.getRaw(OUR_SIGNATURE));


		return sessionState;
	}


	IntroduceeProtocolState getState() {
		return state;
	}

	void setState(IntroduceeProtocolState state) {
		this.state = state;
	}

	boolean wasLocallyAccepted() {
		return accept;
	}

	void setAccept(boolean accept) {
		if (accepted) {
			this.accept &= accept;
		} else {
			this.accept = accept;
		}
		accepted = true;
	}

	void setAnswered(boolean answered) {
		this.answered = answered;
	}

	boolean getAnswered() {
		return answered;
	}

	long getOurTime() {
		return ourTime;
	}

	void setOurTime(long time) {
		ourTime = time;
	}

	long getTheirTime() {
		return theirTime;
	}

	public void setTheirTime(long time) {
		theirTime = time;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	byte[] getTheirEphemeralPublicKey() {
		return theirEphemeralPublicKey;
	}

	void setTheirEphemeralPublicKey(byte[] theirEphemeralPublicKey) {
		this.theirEphemeralPublicKey = theirEphemeralPublicKey;
	}

	void setOurTransportProperties(BdfDictionary ourTransportProperties) {
		this.ourTransportProperties = ourTransportProperties;
	}

	boolean getContactExists() {
		return contactAlreadyExists;
	}

	void setContactExists(boolean exists) {
		this.contactAlreadyExists = exists;
	}

	void setRemoteAuthorId(AuthorId remoteAuthorId) {
		this.remoteAuthorId = remoteAuthorId;
	}

	AuthorId getRemoteAuthorId() {
		return remoteAuthorId;
	}

	void setRemoteAuthorIsUs(boolean remoteAuthorIsUs) {
		this.remoteAuthorIsUs = remoteAuthorIsUs;
	}

	boolean getRemoteAuthorIsUs() {
		return remoteAuthorIsUs;
	}

	void clearOurKeyPair() {
		this.ourPrivateKey = null;
		this.ourPublicKey = null;
	}

	byte[] getOtherResponseId() {
		return this.otherResponseId;
	}

	void setTheirResponseId(byte[] otherResponse) {
		this.otherResponseId = otherResponse;
	}

	String getMessage() {
		return message;
	}

	void setTask(int task) {
		this.task = task;
	}

	int getTask() {
		return task;
	}

	boolean wasLocallyAcceptedOrDeclined() {
		return accepted;
	}

	void setIntroducedName(String introducedName) {
		this.introducedName = introducedName;
	}

	String getIntroducedName() {
		return introducedName;
	}

	byte[] getOurPublicKey() {
		return ourPublicKey;
	}

	void setOurPublicKey(byte[] ourPublicKey) {
		this.ourPublicKey = ourPublicKey;
	}

	byte[] getOurPrivateKey() {
		return ourPrivateKey;
	}

	void setOurPrivateKey(byte[] ourPrivateKey) {
		this.ourPrivateKey = ourPrivateKey;
	}

	GroupId getIntroductionGroupId() {
		return introductionGroupId;
	}

	byte[] getIntroducedPublicKey() {
		return introducedPublicKey;
	}

	void setIntroducedPublicKey(byte[] introducedPublicKey) {
		this.introducedPublicKey = introducedPublicKey;
	}


	ContactId getIntroducedId() {
		return introducedId;
	}

	void setIntroducedId(
			ContactId introducedId) {
		this.introducedId = introducedId;
	}

	void setIntroducedAuthorId(
			AuthorId introducedAuthorId) {
		this.introducedAuthorId = introducedAuthorId;
	}

	AuthorId getLocalAuthorId() {
		return localAuthorId;
	}

	void setLocalAuthorId(
			AuthorId localAuthorId) {
		this.localAuthorId = localAuthorId;
	}

	ContactId getIntroducerId() {
		return introducerId;
	}

	void setMac(byte[] mac) {
		this.mac = mac;
	}

	byte[] getMac() {
		return mac;
	}

	void setSignature(byte[] signature) {
		this.signature = signature;
	}

	byte[] getSignature() {
		return signature;
	}

	void setOurMac(byte[] ourMac) {
		this.ourMac = ourMac;
	}

	byte[] getOurMac() {
		return ourMac;
	}

	void setOurSignature(byte[] ourSignature) {
		this.ourSignature = ourSignature;
	}

	byte[] getOurSignature() {
		return ourSignature;
	}

	void setOurTransport(BdfDictionary ourTransport) {
		this.ourTransport = ourTransport;
	}

	BdfDictionary getOurTransport() {
		return ourTransport;
	}

	void setTheirNonce(byte[] theirNonce) {
		this.theirNonce = theirNonce;
	}

	byte[] getTheirNonce() {
		return theirNonce;
	}

	void setTheirMacKey(byte[] theirMacKey) {
		this.theirMacKey = theirMacKey;
	}

	byte[] getTheirMacKey() {
		return this.theirMacKey;
	}

	BdfDictionary getOurTransportProperties() {
		return ourTransportProperties;
	}

	String getIntroducerName() {
		return introducerName;
	}
}
