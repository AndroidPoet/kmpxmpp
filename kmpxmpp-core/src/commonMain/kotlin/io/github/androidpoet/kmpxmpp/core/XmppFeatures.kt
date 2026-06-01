package io.github.androidpoet.kmpxmpp.core

@JvmInline
public value class XmppFeatureId(public val value: String) {
    init {
        require(value.isNotBlank()) { "XMPP feature id cannot be blank." }
    }
}

public enum class XmppFeatureStability {
    Stable,
    Experimental,
    Deferred,
    Deprecated,
    Unknown,
}

public data class XmppFeatureDescriptor(
    val id: XmppFeatureId,
    val stability: XmppFeatureStability = XmppFeatureStability.Unknown,
)

public enum class XmppFeaturePolicy {
    StableOnly,
    AllowExperimental,
    AllowDeferred,
    AllowDeprecated,
    AllowAll,
    ;

    public fun allows(stability: XmppFeatureStability): Boolean =
        when (this) {
            StableOnly -> stability == XmppFeatureStability.Stable
            AllowExperimental -> stability == XmppFeatureStability.Stable || stability == XmppFeatureStability.Experimental
            AllowDeferred -> stability == XmppFeatureStability.Stable || stability == XmppFeatureStability.Experimental || stability == XmppFeatureStability.Deferred
            AllowDeprecated -> stability != XmppFeatureStability.Unknown
            AllowAll -> true
        }
}

public class XmppCapabilityRegistry(
    private val policy: XmppFeaturePolicy = XmppFeaturePolicy.AllowAll,
) {
    private val features: MutableMap<XmppFeatureId, XmppFeatureDescriptor> = linkedMapOf()

    public fun register(feature: XmppFeatureDescriptor): XmppResult<Unit> {
        if (!policy.allows(feature.stability)) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Feature ${feature.id.value} blocked by policy ${policy.name}.",
                    stage = XmppErrorStage.StreamNegotiation,
                    recoverable = false,
                ),
            )
        }
        features[feature.id] = feature
        return XmppResult.Success(Unit)
    }

    public fun register(featureId: XmppFeatureId): XmppResult<Unit> = register(
        XmppFeatureDescriptor(
            id = featureId,
            stability = XmppFeatureCatalog.stabilityOf(featureId),
        ),
    )

    public fun supports(featureId: XmppFeatureId): Boolean = features.containsKey(featureId)

    public fun snapshot(): Set<XmppFeatureId> = features.keys.toSet()
}

public object XmppFeatureCatalog {
    public val Carbons: XmppFeatureId = XmppFeatureId("urn:xmpp:carbons:2")
    public val Mam: XmppFeatureId = XmppFeatureId("urn:xmpp:mam:2")
    public val ChatMarkers: XmppFeatureId = XmppFeatureId("urn:xmpp:chat-markers:0")
    public val HttpUpload: XmppFeatureId = XmppFeatureId("urn:xmpp:http:upload:0")
    public val BookmarksLegacy: XmppFeatureId = XmppFeatureId("storage:bookmarks")
    public val Omemo: XmppFeatureId = XmppFeatureId("eu.siacs.conversations.axolotl")

    private val stabilityByFeature: Map<XmppFeatureId, XmppFeatureStability> = mapOf(
        Carbons to XmppFeatureStability.Stable,
        Mam to XmppFeatureStability.Stable,
        ChatMarkers to XmppFeatureStability.Stable,
        HttpUpload to XmppFeatureStability.Stable,
        BookmarksLegacy to XmppFeatureStability.Deprecated,
        Omemo to XmppFeatureStability.Experimental,
    )

    public fun stabilityOf(featureId: XmppFeatureId): XmppFeatureStability =
        stabilityByFeature[featureId] ?: XmppFeatureStability.Unknown
}
