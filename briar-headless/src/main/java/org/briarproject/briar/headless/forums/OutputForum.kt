package org.briarproject.briar.headless.forums

import org.briarproject.briar.api.forum.Forum

internal fun Forum.output() = mapOf(
    "name" to name,
    "id" to id.bytes
)

internal fun Collection<Forum>.output() = map { it.output() }
