package org.briarproject.briar.headless.forums

import org.briarproject.briar.api.forum.Forum

internal fun Forum.output() = OutputForum(this)

internal fun Collection<Forum>.output() = map { it.output() }
