package org.briarproject.bramble.identity

import org.briarproject.bramble.api.identity.Author
import javax.annotation.concurrent.Immutable

@Immutable
@Suppress("unused")
class OutputAuthor(author: Author) {

    val id: ByteArray = author.id.bytes
    val name: String = author.name
    val publicKey: ByteArray = author.publicKey

}
