package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.sync.GroupId;

public interface InvitationFactory<I extends SharingMessage.Invitation> {

	I build(GroupId groupId, BdfDictionary d) throws FormatException;
}
