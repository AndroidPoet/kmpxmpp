package io.github.androidpoet.kmpxmpp.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class XmppCapabilityRegistryTest {
    @Test
    fun test_registry_register_whenPolicyAllows_addsFeature() {
        val registry = XmppCapabilityRegistry(policy = XmppFeaturePolicy.StableOnly)

        val result = registry.register(XmppFeatureCatalog.Carbons)

        assertIs<XmppResult.Success<Unit>>(result)
        assertTrue(registry.supports(XmppFeatureCatalog.Carbons))
    }

    @Test
    fun test_registry_register_whenPolicyBlocks_returnsFailure() {
        val registry = XmppCapabilityRegistry(policy = XmppFeaturePolicy.StableOnly)

        val result = registry.register(XmppFeatureCatalog.Omemo)

        assertIs<XmppResult.Failure>(result)
        assertFalse(registry.supports(XmppFeatureCatalog.Omemo))
    }
}
