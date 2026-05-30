package io.github.androidpoet.kmpxmpp.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class XmppResultTest {
    @Test
    fun test_result_map_whenSuccess_transformsValue() {
        val result = XmppResult.Success(2)

        val mapped = result.map { it * 3 }

        assertEquals(6, mapped.getOrNull())
    }

    @Test
    fun test_result_map_whenFailure_keepsFailure() {
        val result = XmppResult.Failure(XmppError("boom"))

        val mapped = result.map { it.toString() }

        assertIs<XmppResult.Failure>(mapped)
        assertEquals("boom", mapped.error.message)
    }

    @Test
    fun test_result_flatMap_whenSuccess_returnsTransformedResult() {
        val result = XmppResult.Success("ok")

        val transformed = result.flatMap { XmppResult.Success(it.length) }

        assertEquals(2, transformed.getOrNull())
    }

    @Test
    fun test_result_recover_whenFailure_returnsSuccess() {
        val result = XmppResult.Failure(XmppError("missing"))

        val recovered = result.recover { 42 }

        assertEquals(42, recovered.getOrNull())
    }

    @Test
    fun test_result_fold_whenSuccess_usesSuccessBranch() {
        val result = XmppResult.Success(7)

        val folded = result.fold(
            onSuccess = { "v=$it" },
            onFailure = { "e=${it.message}" },
        )

        assertEquals("v=7", folded)
    }

    @Test
    fun test_result_fold_whenFailure_usesFailureBranch() {
        val result = XmppResult.Failure(XmppError("oops"))

        val folded = result.fold(
            onSuccess = { "v=$it" },
            onFailure = { "e=${it.message}" },
        )

        assertEquals("e=oops", folded)
    }

    @Test
    fun test_result_getOrThrow_whenFailure_throwsResultException() {
        val result = XmppResult.Failure(XmppError("bad"))

        try {
            result.getOrThrow()
            fail("Expected XmppResultException")
        } catch (error: XmppResultException) {
            assertEquals("bad", error.message)
        }
    }

    @Test
    fun test_result_of_whenBlockThrows_returnsFailure() {
        val result = xmppResultOf { error("x") }

        assertIs<XmppResult.Failure>(result)
        assertEquals("x", result.error.message)
        assertTrue(result.error.cause is IllegalStateException)
    }

    @Test
    fun test_result_ofSuspend_whenBlockThrows_returnsFailure() = runTest {
        val result = xmppResultOfSuspend { error("suspend-x") }

        assertIs<XmppResult.Failure>(result)
        assertEquals("suspend-x", result.error.message)
    }

    @Test
    fun test_result_helpers_whenFailure_returnNullAndError() {
        val result = XmppResult.Failure(XmppError("nope"))

        assertNull(result.getOrNull())
        assertEquals("nope", result.errorOrNull()?.message)
    }
}
