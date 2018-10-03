package org.briarproject.briar.headless.blogs

import org.briarproject.bramble.api.sync.MessageId
import org.briarproject.bramble.identity.output
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.api.blog.MessageType
import org.briarproject.briar.headless.json.JsonDict

internal fun BlogPostHeader.output(body: String): JsonDict {
    val dict = JsonDict(
        "body" to body,
        "author" to author.output(),
        "authorStatus" to authorStatus.output(),
        "type" to type.output(),
        "id" to id.bytes,
        "read" to isRead,
        "rssFeed" to isRssFeed,
        "timestamp" to timestamp,
        "timestampReceived" to timeReceived
    )
    if (parentId != null) dict.put("parentId", (parentId as MessageId).bytes)
    return dict
}

internal fun MessageType.output() = name.toLowerCase()
