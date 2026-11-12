package com.enochc.hive

/**
 * What a [HiveClient] needs to start a hive. Set [connect] to join a
 * server as a client, the usual case on a phone, or [listen] to be one.
 *
 * @param name what other hives know this one as; peer messages are
 *   addressed by it.
 * @param connect the server's address, `"192.168.1.10:3000"`.
 * @param listen the address to accept clients on, `"0.0.0.0:3000"`.
 * @param properties properties this hive starts with, by name. Values are
 *   [String], [Boolean], [Int]/[Long] or [Float]/[Double]. A client's are
 *   replaced by the server's values for the same names once connected.
 * @param names names of further properties, without a starting value,
 *   for this hive to know from the start.
 * @param syncNames exchange property names with the hives this one is
 *   connected to, so properties are reported under their names without
 *   being listed here. Values travel with ids only; names are sent once,
 *   to hives that ask. Turn it off when any of those hives is older than
 *   Hive 0.3.0, which stops on the request. A property is then only named
 *   if it is in [properties] or [names], or named through
 *   [HiveClient.set] or [HiveClient.registerName].
 * @param authSecret shared secret both ends must hold. Needs a libhive.so
 *   built with the `auth` feature.
 * @param waveTimeoutMs ping the other end after this long without hearing
 *   from it and treat it as gone if that goes unanswered, to notice a link
 *   that died silently (Wi-Fi dropping out).
 * @param extraToml further Hive config appended as is. Since it follows
 *   the `[Properties]` table it can only hold tables of its own, `[TLS]`
 *   for one.
 */
data class HiveConfig(
    val name: String,
    val connect: String? = null,
    val listen: String? = null,
    val properties: Map<String, Any> = emptyMap(),
    val names: List<String> = emptyList(),
    val syncNames: Boolean = true,
    val authSecret: String? = null,
    val waveTimeoutMs: Long? = null,
    val extraToml: String = "",
) {
    /** The config in the form the hive itself reads. */
    fun toToml(): String = buildString {
        append("name = ").append(quote(name)).append('\n')
        connect?.let { append("connect = ").append(quote(it)).append('\n') }
        listen?.let { append("listen = ").append(quote(it)).append('\n') }
        authSecret?.let { append("auth_secret = ").append(quote(it)).append('\n') }
        waveTimeoutMs?.let { append("wave_timeout_ms = ").append(it).append('\n') }
        if (properties.isNotEmpty()) {
            append("[Properties]\n")
            for ((key, value) in properties) {
                append(quote(key)).append(" = ").append(literal(key, value)).append('\n')
            }
        }
        append(extraToml)
    }

    private fun literal(key: String, value: Any): String = when (value) {
        is String -> quote(value)
        is Boolean -> value.toString()
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float, is Double -> {
            val number = (value as Number).toDouble()
            when {
                number.isNaN() -> "nan"
                number.isInfinite() -> if (number > 0) "inf" else "-inf"
                else -> number.toString()
            }
        }
        else -> throw IllegalArgumentException(
            "property \"$key\" is a ${value.javaClass.simpleName}; Hive holds strings, booleans, integers and floats"
        )
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' || c == '\u007f' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}
