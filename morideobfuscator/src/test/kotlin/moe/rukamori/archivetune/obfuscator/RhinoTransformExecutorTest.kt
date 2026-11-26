package moe.rukamori.archivetune.obfuscator

import org.junit.Assert.assertEquals
import org.junit.Test

class RhinoTransformExecutorTest {
    private val executor = RhinoTransformExecutor()

    private fun samplePlan(
        signatureProgram: String? = "function sig(s) { return s.split('').reverse().join(''); }",
        signatureFunction: String? = "sig",
        nProgram: String? = "function nTransform(n) { return n + '_ok'; }",
        nFunction: String? = "nTransform",
    ) = TransformPlan(
        playerId = "test-player",
        playerUrl = "https://example.com/base.js",
        sourceSha256 = "0000",
        signatureTimestamp = 20000,
        signatureProgram = signatureProgram,
        signatureFunction = signatureFunction,
        nProgram = nProgram,
        nFunction = nFunction,
        createdAtMillis = 0L,
    )

    @Test
    fun `executeSignature and executeN evaluate compiled JS transforms`() {
        val plan = samplePlan()
        assertEquals("cba", executor.executeSignature(plan, "abc"))
        assertEquals("xyz_ok", executor.executeN(plan, "xyz"))
    }

    @Test(expected = MoriCipherCapabilityException::class)
    fun `executeSignature throws capability exception when signature program is missing`() {
        val plan = samplePlan(signatureProgram = null, signatureFunction = null)
        executor.executeSignature(plan, "abc")
    }

    @Test(expected = MoriCipherException::class)
    fun `executeSignature rejects empty input`() {
        executor.executeSignature(samplePlan(), "")
    }
}
