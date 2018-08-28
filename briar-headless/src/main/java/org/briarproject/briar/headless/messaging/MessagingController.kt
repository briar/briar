package org.briarproject.briar.headless.messaging

import io.javalin.Context

interface MessagingController {

    fun list(ctx: Context): Context

    fun write(ctx: Context): Context

}
