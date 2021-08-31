package org.briarproject.briar.headless.blogs

import org.briarproject.bramble.identity.output
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.api.blog.MessageType
import org.briarproject.briar.headless.json.JsonDict

internal fun BlogPostHeader.output(text: String) = JsonDict(
    "text" to text,
    "author" to author.output(),
    "authorStatus" to authorInfo.status.output(),
    "type" to type.output(),
    "id" to id.bytes,
    "parentId" to parentId?.bytes,
    "read" to isRead,
    "rssFeed" to isRssFeed,
    "timestamp" to timestamp,
    "timestampReceived" to timeReceived
)

internal fun MessageType.output() = name.toLowerCase()
