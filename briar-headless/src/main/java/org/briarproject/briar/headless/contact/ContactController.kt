package org.briarproject.briar.headless.contact

import io.javalin.Context

interface ContactController {

    fun list(ctx: Context): Context
    fun link(ctx: Context): Context
    fun addPendingContact(ctx: Context): Context
    fun listPendingContacts(ctx: Context): Context
    fun removePendingContact(ctx: Context): Context
    fun delete(ctx: Context): Context

}
