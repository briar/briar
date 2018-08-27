package org.briarproject.briar.headless.forums

import org.briarproject.briar.api.forum.Forum
import javax.annotation.concurrent.Immutable

@Immutable
@Suppress("unused")
internal class OutputForum(forum: Forum) {

    val name: String = forum.name
    val id: ByteArray = forum.id.bytes

}
