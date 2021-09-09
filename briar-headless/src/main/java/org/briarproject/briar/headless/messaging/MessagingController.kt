package org.briarproject.briar.headless.messaging

import io.javalin.http.Context

interface MessagingController {

    fun list(ctx: Context): Context

    fun write(ctx: Context): Context

    fun markMessageRead(ctx: Context): Context

    fun deleteAllMessages(ctx: Context): Context

}
