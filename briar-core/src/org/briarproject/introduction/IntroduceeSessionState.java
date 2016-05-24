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
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_2;
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
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
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
	private byte[] ePublicKey;

	private byte[] mac;
	private byte[] signature;
	private byte[] ourMac;
	private byte[] ourSignature;
	private BdfDictionary ourTransport;
	private byte[] nonce;
	private byte[] macKey;

	private int task;

	private String message;
	private BdfDictionary transport; // FIXME should not be a dictionary

	private boolean answered;
	private boolean accept;
	private boolean accepted;

	private boolean contactAlreadyExists;

	private byte[] otherResponseId;

	private AuthorId remoteAuthorId;
	private boolean remoteAuthorIsUs;

	private String name;
	private GroupId introductionGroupId;
	private ContactId introducedId;
	private String introducedName;
	private AuthorId introducedAuthorId;

	IntroduceeSessionState(MessageId storageId, SessionId sessionId,
			GroupId groupId,
			ContactId introducerId, AuthorId introducerAuthorId,
			String introducerName,  AuthorId introducerLocalAuthorId,
			IntroduceeProtocolState state){

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
		this.contactAlreadyExists= false;
		this.otherResponseId = null;
		this.task = NO_TASK;
		this.transport = null;
		this.introductionGroupId = groupId;

		// these are not set during initialization, so we default them to null
		this.introducedName = null;
		this.introducedAuthorId = null;
		this.introducedId = null;
		this.introducedPublicKey = null;
		this.ourPublicKey = null;
		this.ourPrivateKey = null;
		this.ePublicKey = null;
		this.introducedPublicKey = null;
		this.message = null;
		this.mac = null;
		this.signature = null;
		this.ourMac = null;
		this.ourSignature = null;
		this.ourTransport = null;
		this.nonce = null;
		this.macKey = null;
	}

	public BdfDictionary toBdfDictionary() {
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

		d.put(GROUP_ID_1, introductionGroupId);
		d.put(GROUP_ID, introductionGroupId);

		d.put(AUTHOR_ID_1, introducerAuthorId);
		d.put(CONTACT_1, introducerName);
		d.put(CONTACT_ID_1, introducerId.getInt());

		if (introducedAuthorId != null) {
			d.put(AUTHOR_ID_2, introducedAuthorId);
			d.put(CONTACT_2, introducedName);
			d.put(CONTACT_ID_2, introducedId);
		}
		// TODO check if we really need three names and what this name refers to
		if (name != null) d.put(NAME, name);

		if (remoteAuthorId != null)
			d.put(REMOTE_AUTHOR_ID, remoteAuthorId);

		d.put(LOCAL_AUTHOR_ID, localAuthorId);

		if (transport != null)
			d.put(TRANSPORT, transport);

		if (ourPublicKey != null)
			d.put(OUR_PUBLIC_KEY, ourPublicKey);

		if (ourPrivateKey != null)
			d.put(OUR_PRIVATE_KEY, ourPrivateKey);

		if (ePublicKey != null)
			d.put(E_PUBLIC_KEY, ePublicKey);

		if (introducedPublicKey != null)
			d.put(PUBLIC_KEY, introducedPublicKey);

		if (otherResponseId  != null)
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

		if (nonce != null)
			d.put(NONCE, nonce);

		if (macKey != null) {
			d.put(MAC_KEY, macKey);
		}
		
		d.put(TIME, theirTime);
		d.put(OUR_TIME, ourTime);
		d.put(EXISTS, contactAlreadyExists);
		d.put(TASK, task);

		return d;
	}

	public static IntroduceeSessionState fromBdfDictionary(BdfDictionary d)
			throws FormatException{

		if (d.getLong(ROLE).intValue() != ROLE_INTRODUCEE)
			throw new FormatException();

		MessageId storageId = new MessageId(d.getRaw(STORAGE_ID));
		SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));

		// FIXME: do we need both GROUP_ID and GROUP_ID_1?
		GroupId groupId = new GroupId(d.getRaw(GROUP_ID_1));

		AuthorId iaid = new AuthorId(d.getRaw(AUTHOR_ID_1));
		String iname = d.getString(INTRODUCER);
		ContactId iid = new ContactId(d.getLong(CONTACT_ID_1).intValue());
		AuthorId liaid = new AuthorId(d.getRaw(LOCAL_AUTHOR_ID));

		int stateno = d.getLong(STATE).intValue();
		IntroduceeProtocolState state =
				IntroduceeProtocolState.fromValue(stateno);

		IntroduceeSessionState sessionState = new IntroduceeSessionState(storageId,
				sessionId, groupId, iid, iaid, iname, liaid, state);

		if (d.containsKey(CONTACT_2)) {
			sessionState.setIntroducedName(d.getString(CONTACT_2));
			sessionState
					.setIntroducedAuthorId(new AuthorId(d.getRaw(AUTHOR_ID_2)));
			sessionState.setIntroducedId(
					new ContactId(d.getLong(CONTACT_ID_2).intValue()));
		}

		if (d.containsKey(REMOTE_AUTHOR_ID))
			sessionState.setRemoteAuthorId(new AuthorId(d.getRaw(REMOTE_AUTHOR_ID)));

		if (d.containsKey(TRANSPORT))
			sessionState.setTransport(d.getDictionary(TRANSPORT));

		if (d.containsKey(OUR_PUBLIC_KEY))
			sessionState.ourPublicKey = d.getRaw(OUR_PUBLIC_KEY);

		if (d.containsKey(OUR_PRIVATE_KEY))
			sessionState.ourPrivateKey = d.getRaw(OUR_PRIVATE_KEY);

		if (d.containsKey(E_PUBLIC_KEY))
			sessionState.ePublicKey = d.getRaw(E_PUBLIC_KEY);

		if (d.containsKey(PUBLIC_KEY))
			sessionState.setIntroducedPublicKey(d.getRaw(PUBLIC_KEY));

		if (d.containsKey(ACCEPT))
			sessionState.setAccept(d.getBoolean(ACCEPT));

		if (d.containsKey(NOT_OUR_RESPONSE))
			sessionState.setOtherResponseId(d.getRaw(NOT_OUR_RESPONSE));

		if (d.containsKey(MAC))
			sessionState.setMac(d.getRaw(MAC));

		if (d.containsKey(SIGNATURE))
			sessionState.setSignature(d.getRaw(SIGNATURE));

		if (d.containsKey(OUR_TRANSPORT))
			sessionState.setOurTransport(d.getDictionary(OUR_TRANSPORT));

		sessionState.setTheirTime(d.getLong(TIME));
		sessionState.setOurTime(d.getLong(OUR_TIME));
		sessionState.setName(d.getString(NAME));
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
			sessionState.setNonce(d.getRaw(NONCE));

		if (d.containsKey(MAC_KEY))
			sessionState.setMacKey(d.getRaw(MAC_KEY));

		if (d.containsKey(OUR_MAC)) {
			sessionState.setOurMac(d.getRaw(OUR_MAC));
		}

		if (d.containsKey(OUR_SIGNATURE))
			sessionState.setOurSignature(d.getRaw(OUR_SIGNATURE));


		return sessionState;
	}
	

	public IntroduceeProtocolState getState() {
		return state;
	}

	public void setState(IntroduceeProtocolState state) {
		this.state = state;
	}

	public boolean getAccept() {
		return accept;
	}

	public void setAccept(boolean accept) {
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

	byte[] getEPublicKey() {
		return ePublicKey;
	}

	void setEPublicKey(byte[] ePublicKey) {
		this.ePublicKey = ePublicKey;
	}

	public void setTransport(BdfDictionary transport) {
		this.transport = transport;
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

	void setOtherResponseId(byte[] otherResponse) {
		this.otherResponseId = otherResponse;
	}

	public String getMessage() {
		return message;
	}

	public void setTask(int task) {
		this.task = task;
	}

	public int getTask() {
		return task;
	}

	boolean wasAccepted() {
		return accepted;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String getIntroducerName() {
		return introducerName;
	}

	public byte[] getOurPublicKey() {
		return ourPublicKey;
	}

	public void setOurPublicKey(byte[] ourPublicKey) {
		this.ourPublicKey = ourPublicKey;
	}

	public byte[] getOurPrivateKey() {
		return ourPrivateKey;
	}

	public void setOurPrivateKey(byte[] ourPrivateKey) {
		this.ourPrivateKey = ourPrivateKey;
	}

	public GroupId getIntroductionGroupId() {
		return introductionGroupId;
	}

	public void setIntroductionGroupId(
			GroupId introductionGroupId) {
		this.introductionGroupId = introductionGroupId;
	}

	public byte[] getIntroducedPublicKey() {
		return introducedPublicKey;
	}

	public void setIntroducedPublicKey(byte[] introducedPublicKey) {
		this.introducedPublicKey = introducedPublicKey;
	}

	public String getIntroducedName() {
		return introducedName;
	}

	public void setIntroducedName(String introducedName) {
		this.introducedName = introducedName;
	}

	public ContactId getIntroducedId() {
		return introducedId;
	}

	public void setIntroducedId(
			ContactId introducedId) {
		this.introducedId = introducedId;
	}

	public void setIntroducedAuthorId(
			AuthorId introducedAuthorId) {
		this.introducedAuthorId = introducedAuthorId;
	}

	public AuthorId getLocalAuthorId() {
		return localAuthorId;
	}

	public void setLocalAuthorId(
			AuthorId localAuthorId) {
		this.localAuthorId = localAuthorId;
	}

	public ContactId getIntroducerId() {
		return introducerId;
	}

	public void setMac(byte[] mac) {
		this.mac = mac;
	}

	public byte[] getMac() {
		return mac;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public byte[] getSignature() {
		return signature;
	}

	public void setOurMac(byte[] ourMac) {
		this.ourMac = ourMac;
	}

	public byte[] getOurMac() {
		return ourMac;
	}

	public void setOurSignature(byte[] ourSignature) {
		this.ourSignature = ourSignature;
	}

	public byte[] getOurSignature() {
		return ourSignature;
	}

	public void setOurTransport(BdfDictionary ourTransport) {
		this.ourTransport = ourTransport;
	}

	public BdfDictionary getOurTransport() {
		return ourTransport;
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}

	public byte[] getNonce() {
		return nonce;
	}

	public void setMacKey(byte[] macKey) {
		this.macKey = macKey;
	}

	public byte[] getMacKey() {
		return this.macKey;
	}

	public BdfDictionary getTransport() {
		return transport;
	}
}
