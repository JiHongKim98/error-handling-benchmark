# error-handling-benchmark

## 개요

대부분의 웹 서비스는 **“비밀번호 불일치”**, **“작성자가 아님”** 과 같은 도메인 오류가 요청마다 흔히 발생한다.
이런 예외마다 모두 **스택트레이스**를 생성 또는 로깅하면

- 스택 캡처 오버헤드
- 로그 출력 오버헤드

등이 누적됨으로 인해 TPS에 직접적인 영향을 줄 수 있다.

**정상적인 서비스의 흐름**으로 볼 수 있는 **비즈니스 예외**에서까지 동일한 비용을 지불해야 할까?

따라서, Kotlin에서 예외를 다루는 3가지 방식

- 일반적인 RuntimeException의 `try-catch`
- 함수형 모델의 `Either`
- `Stacktrace`를 억제한 [SuppressedException](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/src/jmh/kotlin/ErrorScenarioBenchmark.kt#L94)의 `try-catch`

의 순수 처리량을 JHM으로 비교하여 비즈니스 예외를 어떻게 처리하는게 좋을지 테스트 진행

## 테스트 환경

| 항목       | 사양                   |
| ---------- | ---------------------- |
| JVM        | JAVA 21, Kotlin 1.9.23 |
| Arrow-core | 1.2.4                  |
| Test-Tool  | JMH 1.37               |
| CPU        | Apple M1 Pro 10-core   |
| MEM        | 16 GB                  |
| OS         | macOS 15.5 (Sequoia)   |

## 테스트 시나리오

전체 코드는 [`ErrorScenarioBenchmark.kt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/src/jmh/kotlin/ErrorScenarioBenchmark.kt) 참고

### 오류율 (`errorProb`)

- 10%

### 측정 지표

- Throughput(ops/s)

### JMH 설정

```groovy
jmh {
   fork = 3              // 측정용 JVM 3회
   warmupForks = 1       // JVM 워밍업 전용 1회
   iterations = 5        // 측정 5회
   warmupIterations = 2  // 워밍업 2회
}
```

### 벤치마크 대상

| 이름                                | 처리 방식                                      | 비고                |
| ----------------------------------- | ---------------------------------------------- | ------------------- |
| **baseline**                        | 단순 산술 연산만 수행                          | 오버헤드 0, 기준선  |
| **tryCatchWithRuntimeException**    | 전통적인 `try-catch` + 일반 `RuntimeException` | Stacktrace **포함** |
| **tryCatchWithSuppressedException** | `RuntimeException(msg, null, false, false)`    | Stacktrace **억제** |
| **useEither**                       | `Either<Error, Value>` 값 반환                 | 함수형 예외 모델    |

## 테스트 결과

| Benchmark                       | Throughput (ops/s) | baseline 대비 |
| ------------------------------- | ------------------ | ------------- |
| baseline                        | 1792711070.995     | 100% (기준)   |
| tryCatchWithRuntimeException    | 14590827.023       | 0.81%         |
| tryCatchWithSuppressedException | 227175554.200      | 12.67%        |
| useEither                       | 226232437.457      | 12.61%        |

테스트 결과 파일은 [`results.txt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/results/results.txt) 참고

## 결과 분석

1. 표준 예외(RuntimeException)
   - `fillInStackTrace()` 호출로 스택 프레임 순회를 수행하여 비용이 가장 큼
   - 오류율 10% 만으로도 baseline 대비 약 123배 처리량 감소
2. 스택트레이스 억제 예외(SuppressedException)
   - `writableStackTrace=false`로 스택 캡처를 제거해 오버헤드 상당 부분 개선
   - 기존 RuntimeException 대비 약 16배 높은 처리량
3. 함수형 예외 처리 모델(Either)
   - 스택트레이스 비용은 없으나 호출마다 `Left/Right` 객체가 할당
   - 오류율 10% 기준 SuppressedException과 거의 동일한 처리량

## 결론

1. 시스템 장애 또는 예상치 못한 오류 (발생 빈도가 매우 낮음)
   - 디버깅을 위해 Stacktrace가 필요한 `RuntimeException` 유지
2. 도메인 규칙 위반처럼 빈번한 비즈니스 예외
   - `Either` 또는 Stacktrace를 억제한 `SuppressedException`으로 전환해 TPS 손실 최소화

## TODO

[ ] 오류율 1% 10% 30% 비교 결과 및 결론 추가
