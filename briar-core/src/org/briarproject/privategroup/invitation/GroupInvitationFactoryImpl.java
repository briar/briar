package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.privategroup.invitation.GroupInvitationFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.invitation.GroupInvitationManager.CLIENT_ID;

class GroupInvitationFactoryImpl implements GroupInvitationFactory {

	private final ContactGroupFactory contactGroupFactory;
	private final ClientHelper clientHelper;

	@Inject
	GroupInvitationFactoryImpl(ContactGroupFactory contactGroupFactory,
			ClientHelper clientHelper) {
		this.contactGroupFactory = contactGroupFactory;
		this.clientHelper = clientHelper;
	}

	@Override
	public byte[] signInvitation(Contact c, GroupId privateGroupId,
			long timestamp, byte[] privateKey) {
		AuthorId creatorId = c.getLocalAuthorId();
		AuthorId memberId = c.getAuthor().getId();
		BdfList token = createInviteToken(creatorId, memberId, privateGroupId,
				timestamp);
		try {
			return clientHelper.sign(token, privateKey);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public BdfList createInviteToken(AuthorId creatorId, AuthorId memberId,
			GroupId privateGroupId, long timestamp) {
		Group contactGroup = contactGroupFactory.createContactGroup(CLIENT_ID,
				creatorId, memberId);
		return BdfList.of(
				0, // TODO: Replace with a namespaced string
				timestamp,
				contactGroup.getId(),
				privateGroupId
		);
	}

}
