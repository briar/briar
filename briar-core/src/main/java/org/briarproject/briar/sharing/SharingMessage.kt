package org.briarproject.briar.sharing

import org.briarproject.bramble.api.sync.GroupId
import org.briarproject.bramble.api.sync.MessageId
import org.briarproject.nullsafety.NotNullByDefault
import javax.annotation.Nullable
import javax.annotation.concurrent.Immutable

@Immutable
@NotNullByDefault
abstract class SharingMessage(
	id: MessageId,
	contactGroupId: GroupId,
	shareableId: GroupId,
	timestamp: Long,
	@Nullable previousMessageId: MessageId?
) {
	private val id = id
	private val contactGroupId = contactGroupId
	private val shareableId = shareableId
	private val timestamp = timestamp

	@Nullable
	private val previousMessageId = previousMessageId

	fun getId(): MessageId = id

	fun getContactGroupId(): GroupId = contactGroupId

	fun getShareableId(): GroupId = shareableId

	fun getTimestamp(): Long = timestamp

	@Nullable
	fun getPreviousMessageId(): MessageId? = previousMessageId
}
