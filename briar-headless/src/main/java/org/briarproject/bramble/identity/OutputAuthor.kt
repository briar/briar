package org.briarproject.bramble.identity

import org.briarproject.bramble.api.identity.Author
import javax.annotation.concurrent.Immutable

@Immutable
data class OutputAuthor(
    val id: ByteArray,
    val name: String,
    val publicKey: ByteArray
) {
    constructor(author: Author) : this(
        id = author.id.bytes,
        name = author.name,
        publicKey = author.publicKey
    )
}
