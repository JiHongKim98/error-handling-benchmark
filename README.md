## 개요

대부분의 웹 서비스에서 **"비밀번호 불일치"**, **"작성자가 아님"** 과 같은 비즈니스적 예외가 요청마다 자주 발생한다.

위와 같은 비즈니스 예외마다 **Stacktrace**를 생성하거나 로깅하면:

* 스택 캡처 오버헤드
* 로그 출력 오버헤드

등이 누적되어 애플리케이션 처리량에 직접적인 영향을 줄 수 있다.

정상적인 비즈니스의 흐름으로 볼 수 있는 **비즈니스적 예외**에서도 동일한 비용을 지불하며 사용하는 것이 맞을까?

## 테스트 목적

Kotlin에서 예외를 처리할 수 있는 3가지 방법인:

1. **RuntimeException**
2. Stacktrace를 억제한 **SuppressedException**
3. **Either<Error, Value>** 함수형 예외 모델

각각의 **순수 처리량(Throughput)** 관점에서 비교

## 테스트 환경

| 항목             | 사양                     |
| -------------- | ---------------------- |
| **JVM**        | Java 21, Kotlin 1.9.23 |
| **Arrow-core** | 1.2.4                  |
| **테스트 도구**     | JMH 1.37               |
| **CPU**        | Apple M1 Pro 10‑core   |
| **메모리**        | 16 GB                  |
| **OS**         | macOS 15.5 (Sequoia)   |

## 테스트 시나리오

* **오류율(errorProb)**: 10%
* **측정 지표**: Throughput (ops/s)
* **JMH 설정**:

  ```groovy
  jmh {
     fork = 3              // 측정용 JVM 3회
     warmupForks = 1       // 워밍업 전용 JVM 1회
     iterations = 5        // 측정 5회
     warmupIterations = 2  // 워밍업 2회
  }
  ```

전체 테스트 코드는 [`ErrorScenarioBenchmark.kt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/src/jmh/kotlin/ErrorScenarioBenchmark.kt) 참고

## JMH 벤치마크 대상

| 이름 | 처리 방식 | 비고 |
| ----------------------------------- | ------------------------------- | ------------------ |
| **baseline**                        | 단순 산술 연산만 수행                | 오버헤드 0, 기준점     |
| **tryCatchWithRuntimeException**    | `RuntimeException` try-catch    | Stacktrace **포함** |
| **tryCatchWithSuppressedException** | `SuppressedException` try-catch | Stacktrace **억제** |
| **useEither**                       | `Either<Error, Value>` 반환      | 함수형 예외 모델       |

`tryCatchWithSuppressedException`에 사용된 `SuppressedException` 클래스:

```kotlin
// SuppressedException 클래스

sealed class SuppressedException(
    message: String,
) : RuntimeException(message, null, false, false) {
    data object FailedError : SuppressedException(
        message = "FAILED",
    ) {
        private fun readResolve(): Any = FailedError
    }
}
```

## 테스트 결과

| Benchmark | Throughput (ops/s) | baseline 대비 |
| ----------------------------------- | ------------------ | ------------ |
| **baseline**                        | 1,792,711,070.995  | 100% (기준)   |
| **tryCatchWithRuntimeException**    | 14,590,827.023     | 0.81%        |
| **tryCatchWithSuppressedException** | 227,175,554.200    | 12.67%       |
| **useEither**                       | 226,232,437.457    | 12.61%       |

테스트 결과 파일은 [`results.txt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/results/results.txt) 참고

## 결과 분석

1. **RuntimeException**

   * `fillInStackTrace()` 호출로 스택 프레임 순회 비용이 가장 큼
   * 오류율 10% 만으로도 baseline 대비 약 123배 처리량 감소

2. **SuppressedException**

   * `writableStackTrace=false` 설정으로 Stacktrace 캡처 제거
   * 기존 RuntimeException 대비 약 16배 높은 처리량

3. **Either**

   * Stacktrace 비용은 없지만, 매 호출마다 `Left`/`Right` 객체 생성
   * 오류율 10% 기준 SuppressedException과 처리량이 거의 동일

## 결론

1. **시스템 장애 또는 예상치 못한 오류** (발생 빈도 매우 낮음)

   * 디버깅을 위해 **Stacktrace 포함한 기본적인 RuntimeException** 사용하는 전략

2. **도메인 규칙 위반 등 빈번한 비즈니스 예외**

   * **Either** 또는 **SuppressedException** 사용으로 TPS 손실 최소화하는 전략

## TODO

* [ ] 오류율 1%, 10%, 30% 비교 결과 및 결론 추가
