package org.briarproject.briar.headless.json

class JsonDict(vararg pairs: Pair<String, Any?>) : HashMap<String, Any?>(pairs.size) {
    init {
        putAll(pairs)
    }

    fun putAll(vararg pairs: Pair<String, Any?>) {
        for (p in pairs) put(p.first, p.second)
    }
}
