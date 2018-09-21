package org.briarproject.briar.headless.blogs

import io.javalin.Context

interface BlogController {

    fun listPosts(ctx: Context): Context

    fun createPost(ctx: Context): Context

}
