package org.briarproject.briar.headless.contact

import io.javalin.http.Context

interface ContactController {

    fun list(ctx: Context): Context
    fun getLink(ctx: Context): Context
    fun addPendingContact(ctx: Context): Context
    fun listPendingContacts(ctx: Context): Context
    fun removePendingContact(ctx: Context): Context
    fun setContactAlias(ctx: Context): Context
    fun delete(ctx: Context): Context

}
