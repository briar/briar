package org.briarproject.briar.headless.forums

import io.javalin.BadRequestResponse
import io.javalin.Context
import org.briarproject.briar.api.forum.ForumManager
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
class ForumController @Inject
constructor(private val forumManager: ForumManager) {

    fun list(ctx: Context): Context {
        return ctx.json(forumManager.forums.output())
    }

    fun create(ctx: Context): Context {
        val name = ctx.formParam("name")
        if (name == null || name.isEmpty())
            throw BadRequestResponse("Expecting Forum Name")
        return ctx.json(forumManager.addForum(name).output())
    }

}
