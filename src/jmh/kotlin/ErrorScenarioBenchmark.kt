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
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
open class ErrorScenarioBenchmark {
    private val errorRandom = Random(1234567890)

    private val businessRandom = Random(9876543210)

    private lateinit var errorScenarios: BooleanArray

    @Param("0.01", "0.1", "0.3")
    private var errorProb: Double = 0.0

    private var scenarioSize: Int = 1_000_000

    private var currentScenarioIndex = 0

    @Setup(Level.Trial)
    fun init() {
        errorScenarios = BooleanArray(scenarioSize) {
            errorRandom.nextDouble() < errorProb
        }
    }

    private fun shouldThrowError(): Boolean =
        errorScenarios[currentScenarioIndex++].also {
            currentScenarioIndex %= scenarioSize
        }

    /**
     * 순수 비즈니스 로직 성능 측정 (기준점)
     */
    @Benchmark
    fun baseline(): Int {
        val randomA = businessRandom.nextInt(1000, 9999)
        val randomB = businessRandom.nextInt(1000, 9999)
        return randomA + randomB
    }

    /**
     * Either 기반 예외 처리 (비싱글톤)
     *
     * - 실패 시 새로운 BaseError.Instance(message) 객체 생성 -> 할당량 포함
     * - 성공 시 Right(a+b)
     */
    @Benchmark
    fun eitherError(): Int {
        val result = if (shouldThrowError()) {
            Either.Left(EitherError.Instance(FAILED_MESSAGE))
        } else {
            val randomA = businessRandom.nextInt(1000, 9999)
            val randomB = businessRandom.nextInt(1000, 9999)
            Either.Right(randomA + randomB)
        }
        return result.fold({ -1 }, { it })
    }

    /**
     * Either 기반 예외 처리 (싱글톤)
     *
     * - 실패 시 EitherError.SingletonInstance 재사용 -> 할당 비용 제거
     * - 성공 시 Right(a+b)
     */
    @Benchmark
    fun eitherErrorSingleton(): Int {
        val result = if (shouldThrowError()) {
            Either.Left(EitherError.SingletonInstance)
        } else {
            val randomA = businessRandom.nextInt(1000, 9999)
            val randomB = businessRandom.nextInt(1000, 9999)
            Either.Right(randomA + randomB)
        }
        return result.fold({ -1 }, { it })
    }

    /**
     * 표준 RuntimeException 사용
     *
     * - 실패 시 새로운 RuntimeException 생성 -> 스택트레이스 수집 및 할당 발생
     */
    @Benchmark
    fun runtimeException(): Int =
        try {
            if (shouldThrowError()) {
                throw RuntimeException(FAILED_MESSAGE)
            } else {
                val randomA = businessRandom.nextInt(1000, 9999)
                val randomB = businessRandom.nextInt(1000, 9999)
                randomA + randomB
            }
        } catch (e: Exception) {
            -1
        }

    /**
     * SuppressedException 사용 (비싱글톤)
     *
     * - 실패 시 SuppressedException.Instance(message) 생성 -> 할당 포함
     * - 스택트레이스 억제하여 비용 절감 효과 일부만 제거
     */
    @Benchmark
    fun suppressedException(): Int =
        try {
            if (shouldThrowError()) {
                throw SuppressedException.Instance(FAILED_MESSAGE)
            } else {
                val randomA = businessRandom.nextInt(1000, 9999)
                val randomB = businessRandom.nextInt(1000, 9999)
                randomA + randomB
            }
        } catch (e: Exception) {
            -1
        }

    /**
     * SuppressedException 사용 (싱글톤)
     *
     * - 실패 시 SuppressedException.SingletonInstance 재사용 -> 할당 비용 제거
     * - 순수 예외 처리 비용 비교
     */
    @Benchmark
    fun suppressedExceptionWithSingleton(): Int =
        try {
            if (shouldThrowError()) {
                throw SuppressedException.SingletonInstance
            } else {
                val randomA = businessRandom.nextInt(1000, 9999)
                val randomB = businessRandom.nextInt(1000, 9999)
                randomA + randomB
            }
        } catch (e: Exception) {
            -1
        }
}

private const val FAILED_MESSAGE = "FAILED"

/**
 * Either 실패를 나타내는 에러 객체
 *
 * - Instance: 매번 new 하여 할당 비용 반영
 * - SingletonInstance: 싱글톤 재사용
 */
sealed interface EitherError {
    val message: String

    open class Instance(
        override val message: String,
    ) : EitherError

    data object SingletonInstance : Instance(FAILED_MESSAGE) {
        private fun readResolve(): Any = SingletonInstance
    }
}

/**
 * Stacktrace 억제 예외
 *
 * - Instance: 매번 new 하여 할당 비용(스택억제 포함)
 * - SingletonInstance: 싱글톤 재사용(스택억제 포함)
 */
sealed class SuppressedException(
    message: String,
) : RuntimeException(message, null, false, false) {
    open class Instance(
        override val message: String,
    ) : SuppressedException(message)

    data object SingletonInstance : Instance(FAILED_MESSAGE) {
        private fun readResolve(): Any = SingletonInstance
    }
}
