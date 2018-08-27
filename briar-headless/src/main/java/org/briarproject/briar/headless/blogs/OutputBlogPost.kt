package org.briarproject.briar.headless.blogs

import org.briarproject.bramble.identity.OutputAuthor
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.headless.output
import javax.annotation.concurrent.Immutable

@Immutable
@Suppress("unused")
internal class OutputBlogPost(header: BlogPostHeader, val body: String) {

    val author: OutputAuthor = OutputAuthor(header.author)
    val authorStatus: String = header.authorStatus.output()
    val type = header.type.output()
    val id: ByteArray = header.id.bytes
    val parentId = header.parentId?.bytes
    val read = header.isRead
    val rssFeed = header.isRssFeed
    val timestamp = header.timestamp
    val timestampReceived = header.timeReceived

}
