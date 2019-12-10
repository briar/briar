package org.briarproject.briar.headless.blogs

import io.javalin.http.Context

interface BlogController {

    fun listPosts(ctx: Context): Context

    fun createPost(ctx: Context): Context

}
