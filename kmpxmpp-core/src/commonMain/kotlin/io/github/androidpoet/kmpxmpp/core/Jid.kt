package io.github.androidpoet.kmpxmpp.core

public data class Jid(
    val local: String?,
    val domain: String,
    val resource: String? = null,
) {
    override fun toString(): String {
        val localPart = local?.let { "$it@" } ?: ""
        val resourcePart = resource?.let { "/$it" } ?: ""
        return "$localPart$domain$resourcePart"
    }
}
