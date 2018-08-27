package org.briarproject.briar.headless

import org.briarproject.bramble.api.identity.Author
import org.briarproject.bramble.identity.OutputAuthor
import org.briarproject.briar.api.blog.MessageType

fun Author.output() = OutputAuthor(this)

fun Author.Status.output() = name.toLowerCase()

fun MessageType.output() = name.toLowerCase()

