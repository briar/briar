package org.briarproject.briar.headless.forums

import org.briarproject.briar.api.forum.Forum
import org.briarproject.briar.headless.json.JsonDict

internal fun Forum.output() = JsonDict(
    "name" to name,
    "id" to id.bytes
)

internal fun Collection<Forum>.output() = map { it.output() }
