/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function

internal class RhinoTransformExecutor {
    fun executeSignature(
        plan: TransformPlan,
        input: String,
    ): String =
        execute(
            program = plan.signatureProgram ?: throw MoriCipherCapabilityException("Signature transform is unavailable"),
            functionName = plan.signatureFunction ?: throw MoriCipherCapabilityException("Signature transform is unavailable"),
            input = input,
        )

    fun executeN(
        plan: TransformPlan,
        input: String,
    ): String =
        execute(
            program = plan.nProgram ?: throw MoriCipherCapabilityException("Throttle transform is unavailable"),
            functionName = plan.nFunction ?: throw MoriCipherCapabilityException("Throttle transform is unavailable"),
            input = input,
        )

    private fun execute(
        program: String,
        functionName: String,
        input: String,
    ): String {
        if (input.length !in 1..MAX_INPUT_LENGTH) {
            throw MoriCipherException("Cipher input length was invalid")
        }
        return try {
            factory.call(
                ContextAction { context ->
                    context.setInterpretedMode(true)
                    context.languageVersion = Context.VERSION_ES6
                    context.setClassShutter(ClassShutter { false })
                    val scope = context.initSafeStandardObjects(null, true)
                    context.evaluateString(scope, program, "mori-player", 1, null)
                    val function =
                        scope.get(functionName, scope) as? Function
                            ?: throw MoriCipherException("Compiled transform was not callable")
                    val value = function.call(context, scope, scope, arrayOf(input))
                    Context
                        .toString(value)
                        .takeIf { it.length in 1..MAX_OUTPUT_LENGTH && SAFE_OUTPUT.matches(it) }
                        ?: throw MoriCipherException("Transform produced an invalid value")
                },
            )
        } catch (error: MoriCipherException) {
            throw error
        } catch (error: Exception) {
            throw MoriCipherException("JavaScript transform execution failed", error)
        }
    }

    private class BoundedContextFactory : ContextFactory() {
        override fun makeContext(): Context =
            super.makeContext().apply {
                instructionObserverThreshold = INSTRUCTION_CHUNK
                putThreadLocal(DEADLINE_KEY, System.nanoTime() + MAX_EXECUTION_NANOS)
            }

        override fun observeInstructionCount(
            context: Context,
            instructionCount: Int,
        ) {
            val deadline = context.getThreadLocal(DEADLINE_KEY) as? Long ?: return
            if (System.nanoTime() > deadline) {
                throw EvaluatorException("JavaScript transform exceeded its execution budget")
            }
        }
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 16_384
        const val MAX_OUTPUT_LENGTH = 32_768
        const val INSTRUCTION_CHUNK = 10_000
        const val MAX_EXECUTION_NANOS = 2_000_000_000L
        val DEADLINE_KEY = Any()
        val SAFE_OUTPUT = Regex("""^[A-Za-z0-9._~!$&'()*+,;=:@/?%-]+$""")
        val factory = BoundedContextFactory()
    }
}
