package org.briarproject.bramble.identity

import org.briarproject.bramble.api.identity.Author
import org.briarproject.briar.api.identity.AuthorInfo
import org.briarproject.briar.headless.json.JsonDict

fun Author.output() = JsonDict(
    "formatVersion" to formatVersion,
    "id" to id.bytes,
    "name" to name,
    "publicKey" to publicKey.encoded
)

fun AuthorInfo.Status.output() = name.toLowerCase()
