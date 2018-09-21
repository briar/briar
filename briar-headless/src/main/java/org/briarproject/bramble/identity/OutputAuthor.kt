package org.briarproject.bramble.identity

import org.briarproject.bramble.api.identity.Author

fun Author.output() = mapOf(
    "formatVersion" to formatVersion,
    "id" to id.bytes,
    "name" to name,
    "publicKey" to publicKey
)

fun Author.Status.output() = name.toLowerCase()

