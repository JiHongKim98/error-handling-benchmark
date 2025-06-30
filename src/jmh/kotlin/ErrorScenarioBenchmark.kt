package benchmark

import arrow.core.Either
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
open class ErrorScenarioBenchmark {
    private val random = Random(1234567890)

    private lateinit var errorScenarios: BooleanArray

    @Param("0.1")
    private var errorProb: Double = 0.0

    private var scenarioSize: Int = 100_000

    private var currentScenarioIndex = 0

    @Setup(Level.Trial)
    fun init() {
        errorScenarios = BooleanArray(scenarioSize) {
            random.nextDouble() < errorProb
        }
    }

    private fun shouldThrowError(): Boolean =
        errorScenarios[currentScenarioIndex++].also {
            currentScenarioIndex %= scenarioSize
        }

    @Benchmark
    fun baseline(blackhole: Blackhole) {
        val result = 1 + 1
        blackhole.consume(result)
    }

    @Benchmark
    fun useEither(blackhole: Blackhole) {
        val result = if (shouldThrowError()) {
            Either.Left(FailedError())
        } else {
            Either.Right(1 + 1)
        }
        blackhole.consume(result)
    }

    @Benchmark
    fun tryCatchWithRuntimeException(blackhole: Blackhole) {
        val result = try {
            if (shouldThrowError()) {
                throw RuntimeException("FAILED")
            } else {
                1 + 1
            }
        } catch (e: Exception) {
            -1
        }
        blackhole.consume(result)
    }

    @Benchmark
    fun tryCatchWithSuppressedException(blackhole: Blackhole) {
        val result = try {
            if (shouldThrowError()) {
                throw SuppressedException.FailedError
            } else {
                1 + 1
            }
        } catch (e: Exception) {
            -1
        }
        blackhole.consume(result)
    }
}

@JvmInline
value class FailedError(
    val message: String = "FAILED",
)

sealed class SuppressedException(
    message: String,
) : RuntimeException(message, null, false, false) {
    data object FailedError : SuppressedException(
        message = "FAILED",
    ) {
        private fun readResolve(): Any = FailedError
    }
}
