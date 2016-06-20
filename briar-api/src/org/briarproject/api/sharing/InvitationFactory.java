package org.briarproject.api.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;

public interface InvitationFactory<I extends SharingMessage.Invitation> {

	I build(GroupId groupId, BdfDictionary d) throws FormatException;
}
