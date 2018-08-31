package org.briarproject.briar.headless.blogs

import org.briarproject.bramble.identity.OutputAuthor
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.headless.output
import javax.annotation.concurrent.Immutable

@Immutable
internal data class OutputBlogPost(
    val body: String,
    val author: OutputAuthor,
    val authorStatus: String,
    val type: String,
    val id: ByteArray,
    val parentId: ByteArray?,
    val read: Boolean,
    val rssFeed: Boolean,
    val timestamp: Long,
    val timestampReceived: Long
) {
    internal constructor(header: BlogPostHeader, body: String) : this(
        body = body,
        author = OutputAuthor(header.author),
        authorStatus = header.authorStatus.output(),
        type = header.type.output(),
        id = header.id.bytes,
        parentId = header.parentId?.bytes,
        read = header.isRead,
        rssFeed = header.isRssFeed,
        timestamp = header.timestamp,
        timestampReceived = header.timeReceived
    )
}
