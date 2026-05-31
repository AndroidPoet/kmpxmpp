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

public fun parseJidOrNull(value: String): Jid? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val bare = trimmed.substringBefore('/')
    if (bare.isEmpty()) return null
    val hasLocal = bare.contains("@")
    val local = if (hasLocal) bare.substringBefore('@').ifEmpty { return null } else null
    val domain = if (hasLocal) bare.substringAfter('@') else bare
    if (domain.isBlank()) return null
    val resource = trimmed.substringAfter('/', "").ifBlank { null }
    return Jid(local = local, domain = domain, resource = resource)
}
