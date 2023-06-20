package org.briarproject.briar.headless

import io.javalin.Javalin
import io.javalin.core.util.Header.AUTHORIZATION
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleJavaEagerSingletons
import org.briarproject.bramble.api.crypto.CryptoComponent
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.api.test.TestDataCreator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File

const val port = 8000
const val url = "http://127.0.0.1:$port/v1"
const val token = "authToken"

@TestInstance(PER_CLASS)
abstract class IntegrationTest {

    private val dataDir = File("tmp")

    private val client: OkHttpClient = OkHttpClient()
    protected lateinit var api: Javalin
    protected lateinit var crypto: CryptoComponent
    protected lateinit var testDataCreator: TestDataCreator
    private lateinit var router: Router

    @BeforeAll
    fun setUp() {
        val app = DaggerBriarHeadlessTestApp.builder()
            .headlessTestModule(HeadlessTestModule(dataDir))
            .build()
        BrambleCoreEagerSingletons.Helper.injectEagerSingletons(app)
        BrambleJavaEagerSingletons.Helper.injectEagerSingletons(app)
        BriarCoreEagerSingletons.Helper.injectEagerSingletons(app)
        HeadlessEagerSingletons.Helper.injectEagerSingletons(app)
        router = app.getRouter()
        crypto = app.getCryptoComponent()
        testDataCreator = app.getTestDataCreator()

        api = router.start(token, port, false)
    }

    @AfterAll
    fun tearDown() {
        router.stop()
        dataDir.deleteRecursively()
    }

    protected fun get(url: String, authToken: String = token): Response {
        val request: Request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $authToken")
            .build()
        return client.newCall(request).execute()
    }

    protected fun getWithWrongToken(url: String): Response {
        return get(url, "wrongToken")
    }

    protected fun post(url: String, data: String, authToken: String = token): Response {
        val json = "application/json; charset=utf-8".toMediaType()
        val body = data.toRequestBody(json)
        val request: Request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $authToken")
            .post(body)
            .build()
        return client.newCall(request).execute()
    }

    protected fun postWithWrongToken(url: String): Response {
        return post(url, data = "", authToken = "wrongToken")
    }

    protected fun delete(url: String, authToken: String = token): Response {
        val request: Request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $authToken")
            .delete()
            .build()
        return client.newCall(request).execute()
    }

    protected fun delete(url: String, data: String, authToken: String = token): Response {
        val json = "application/json; charset=utf-8".toMediaType()
        val body = data.toRequestBody(json)
        val request: Request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, "Bearer $authToken")
            .delete(body)
            .build()
        return client.newCall(request).execute()
    }

    protected fun deleteWithWrongToken(url: String): Response {
        return delete(url, authToken = "wrongToken")
    }

}
