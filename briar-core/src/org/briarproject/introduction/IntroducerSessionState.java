package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroducerProtocolState;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY1;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY2;
import static org.briarproject.api.introduction.IntroductionConstants.RESPONSE_1;
import static org.briarproject.api.introduction.IntroductionConstants.RESPONSE_2;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;

class IntroducerSessionState extends IntroductionState {

	private IntroducerProtocolState state;

	private byte[] publicKey1;
	private MessageId response1;
	private final GroupId group1Id;
	private final ContactId contact1Id;
	private final AuthorId contact1AuthorId;
	private final String contact1Name;

	private byte[] publicKey2;
	private MessageId response2;
	private final GroupId group2Id;
	private final ContactId contact2Id;
	private final AuthorId contact2AuthorId;
	private final String contact2Name;


	IntroducerSessionState(MessageId storageId, SessionId sessionId,
			GroupId group1Id, GroupId group2Id, ContactId contact1Id,
			AuthorId contact1AuthorId, String contact1Name,
			ContactId contact2Id, AuthorId contact2AuthorId, String contact2Name,
			IntroducerProtocolState state){

		super(sessionId, storageId);

		this.group2Id = group2Id;
		this.group1Id = group1Id;

		this.contact2Id = contact2Id;
		this.contact1Id = contact1Id;

		this.contact1AuthorId = contact1AuthorId;
		this.contact2AuthorId = contact2AuthorId;

		this.contact1Name = contact1Name;
		this.contact2Name = contact2Name;

		this.state = state;
		this.response1 = null;
		this.response2 = null;
		this.publicKey1 = null;
		this.publicKey2 = null;

	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(ROLE, ROLE_INTRODUCER);

		d.put(GROUP_ID_1, getGroup1Id());
		d.put(GROUP_ID_2, getGroup2Id());

		d.put(STATE, getState().getValue());

		d.put(CONTACT_1, contact1Name);
		d.put(CONTACT_ID_1, contact1Id.getInt());
		d.put(AUTHOR_ID_1, contact1AuthorId);

		d.put(CONTACT_2, contact2Name);
		d.put(CONTACT_ID_2, contact2Id.getInt());
		d.put(AUTHOR_ID_2, contact2AuthorId);

		if (publicKey1 != null)
			d.put(PUBLIC_KEY1, publicKey1);
		if (publicKey2 != null)
			d.put(PUBLIC_KEY2, publicKey2);

		if (response1 != null)
			d.put(RESPONSE_1, response1);
		if (response2 != null)
			d.put(RESPONSE_2, response2);

		return d;
	}

	public static IntroducerSessionState fromBdfDictionary(BdfDictionary d)
			throws FormatException{

		MessageId storageId = new MessageId(d.getRaw(STORAGE_ID));
		SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));

		AuthorId aid1 = new AuthorId(d.getRaw(AUTHOR_ID_1));
		AuthorId aid2 = new AuthorId(d.getRaw(AUTHOR_ID_2));

		String author1 = d.getString(CONTACT_1);
		String author2 = d.getString(CONTACT_2);
		ContactId cid1 = new ContactId(d.getLong(CONTACT_ID_1).intValue());
		ContactId cid2 = new ContactId(d.getLong(CONTACT_ID_2).intValue());

		GroupId group1Id = new GroupId(d.getRaw(GROUP_ID_1));
		GroupId group2Id = new GroupId(d.getRaw(GROUP_ID_2));


		int stateno = d.getLong(STATE).intValue();
		IntroducerProtocolState state = IntroducerProtocolState.fromValue(stateno);
		IntroducerSessionState newstate = new IntroducerSessionState(storageId,
				sessionId, group1Id, group2Id, cid1, aid1, author1, cid2, aid2,
				author2, state);

		if (d.containsKey(PUBLIC_KEY1))
			newstate.setPublicKey1(d.getRaw(PUBLIC_KEY1));

		if (d.containsKey(PUBLIC_KEY2))
			newstate.setPublicKey2(d.getRaw(PUBLIC_KEY2));

		if (d.containsKey(RESPONSE_1))
			newstate.setResponse1(new MessageId(d.getRaw(RESPONSE_1)));
		if (d.containsKey(RESPONSE_2))
			newstate.setResponse2(new MessageId(d.getRaw(RESPONSE_2)));

		return newstate;
	}

	GroupId getGroup2Id() {
		return group2Id;
	}

	public IntroducerProtocolState getState() {
		return state;
	}

	public void setState(IntroducerProtocolState state) {
		this.state = state;
	}

	MessageId getResponse1() {
		return this.response1;
	}

	void setResponse1(MessageId response1) {
		this.response1 = response1;
	}

	MessageId getResponse2() {
		return this.response2;
	}

	void setResponse2(MessageId response2) {
		this.response2 = response2;
	}

	public void setPublicKey1(byte[] publicKey1) {
		this.publicKey1 = publicKey1;
	}

	public void setPublicKey2(byte[] publicKey2) {
		this.publicKey2 = publicKey2;
	}

	public GroupId getGroup1Id() {
		return this.group1Id;
	}
	public ContactId getContact2Id() {
		return contact2Id;
	}

	public AuthorId getContact2AuthorId() {
		return contact2AuthorId;
	}

	public String getContact2Name() {
		return contact2Name;
	}

	public String getContact1Name() {
		return contact1Name;
	}

	public ContactId getContact1Id() {
		return contact1Id;
	}

	public AuthorId getContact1AuthorId() {
		return contact1AuthorId;
	}

}


