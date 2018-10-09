package org.briarproject.briar.headless.contact

import io.javalin.Context

interface ContactController {

    fun list(ctx: Context): Context

}
