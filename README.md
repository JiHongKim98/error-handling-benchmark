## 개요

대부분의 웹 서비스에서 **"비밀번호 불일치"**, **"작성자가 아님"** 과 같은 비즈니스적 예외가 요청마다 자주 발생한다.

위와 같은 비즈니스 예외마다 **Stacktrace**를 생성하거나 로깅하면:

- 스택 캡처 오버헤드
- 로그 출력 오버헤드

등이 누적되어 애플리케이션 처리량에 직접적인 영향을 줄 수 있다.

정상적인 비즈니스의 흐름으로 볼 수 있는 **비즈니스적 예외**에서도 동일한 비용을 지불하며 사용하는 것이 맞을까?

## 테스트 목적

Kotlin에서 예외를 처리할 수 있는 3가지 방법인:

1. **RuntimeException**
2. Stacktrace를 억제한 **SuppressedException**
3. **Either<Error, Value>** 함수형 예외 모델

각 전략에 대해 **싱글톤 재사용 여부**에 따른 할당 비용 차이도 함께 측정하여 사용 전략 구축.

## 테스트 환경

| 항목 | 사양 |
| -------------- | ---------------------- |
| **JVM**        | Java 21, Kotlin 1.9.23 |
| **Arrow-core** | 1.2.4                  |
| **테스트 도구**   | JMH 1.37               |
| **CPU**        | Apple M1 Pro 10‑core   |
| **메모리**       | 16 GB                  |
| **OS**         | macOS 15.5 (Sequoia)   |

## 테스트 시나리오

- **오류율(errorProb)**: 1% / 10% / 30%
- **측정 지표**: Throughput (ops/s)
- **JMH 설정**:

  ```groovy
  jmh {
     fork = 3              // 측정용 JVM 3회
     warmupForks = 1       // 워밍업 전용 JVM 1회
     iterations = 5        // 측정 5회
     warmupIterations = 2  // 워밍업 2회
  }
  ```

- **`EitherError`과 에 사용된 `SuppressedException` 클래스**:

  ```kotlin
  // EitherError 클래스
  sealed interface EitherError {
      val message: String
  
      // 비싱글톤
      open class Instance(
          override val message: String,
      ) : EitherError
  
      // 싱글톤
      data object SingletonInstance : Instance(FAILED_MESSAGE) {
          private fun readResolve(): Any = SingletonInstance
      }
  }
  
  // SuppressedException 클래스
  sealed class SuppressedException(
      message: String,
  ) : RuntimeException(message, null, false, false) {
      // 비싱글톤
      open class Instance(
          override val message: String,
      ) : SuppressedException(message)
  
      // 싱글톤
      data object SingletonInstance : Instance(FAILED_MESSAGE) {
          private fun readResolve(): Any = SingletonInstance
      }
  }
  ```

전체 테스트 코드는 [`ErrorScenarioBenchmark.kt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/src/jmh/kotlin/ErrorScenarioBenchmark.kt) 참고

## 테스트 결과

|  | 에러율 1% (ops/s) | 에러율 10% (ops/s) | 에러율 30% (ops/s) |
| ---------------------------------------- | -------------------- | -------------------- | -------------------- |
| baseline                                 | 215,204,867 <br/> (100%)   | 215,468,070 <br/> (100%)   | 214,801,889 <br/> (100%)   |
| runtimeException             | 79,907,851 <br/> (37.13%)  | 13,655,039 <br/> (6.34%)   | 4,846,679 <br/> (2.26%)    |
| suppressedException          | 162,859,646 <br/> (75.68%) | 145,488,242 <br/> (67.52%) | 114,031,863 <br/> (53.10%) |
| suppressedExceptionWithSingleton | 211,190,290 <br/> (98.13%) | 207,712,218 <br/> (96.40%) | 182,137,598 <br/> (84.80%) |
| either                                | 145,141,856 <br/> (67.44%) | 136,870,922 <br/> (63.52%) | 118,797,754 <br/> (55.31%) |
| eitherWithSingleton                   | 148,588,796 <br/> (69.05%) | 142,431,633 <br/> (66.10%) | 129,384,834 <br/> (60.23%) |

테스트 결과 파일은 [`results.txt`](https://github.com/JiHongKim98/error-handling-benchmark/blob/main/results/results.txt) 참고

## 결과 분석

1. **RuntimeException**

   - `fillInStackTrace()` 호출로 스택 프레임 순회 비용이 가장 큼
   - 즉, 예외 발생시 매번 예외 객체를 생성하여 Stacktrace 캡처 수행으로 인해 가장 큰 손실을 보임

2. **SuppressedException**

   - `writableStackTrace=false` 설정으로 Stacktrace 오버헤드 제거
   - RuntimeException 대비 10 ~ 30배 좋은 처리율을 보임
   - 예외 클래스를 재사용(싱글톤)할 경우 baseline 대비 85% ~ 98%로 가장 높은 처리율을 보임

4. **Either**

   - `Throwable`을 상속하지 않아 Stacktrace 비용이 없음
   - 하지만, 매 호출마다 `Left`/`Right` 래퍼 객체 오버헤드가 있음
   - 오류 객체를 재사용(싱글톤)해도 크게 처리율이 개선되지 않음 (약 3% ~ 9% 정도)

## 결론

- 빈번히 발생하는 비즈니스적 예외에서 Stacktrace를 포함한 예외 발생은 지양
- 발생 빈도가 매우 낮은 시스템 장애 혹은 예상치 못한 오류에 한에 사용하는 것이 바람직
- 비즈니스적 예외에서는 `SuppressedException` 혹은 `Either` 와 같은 경량 예외 객체를 사용
- 성공과 실패에 따른 비즈니스 로직이 여러 단계에 걸쳐있을 경우 성공과 실패를 모두 반환하는 `Either` 사용 권장

(Either 에 대한 usecase 아티클 - [코틀린 함수형 프로그래밍의 길을 찾아서](https://tech.kakaopay.com/post/way-to-functional-programming/#either를-활용한-결제-프로세스-개선-사례))
