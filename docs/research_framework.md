# 프레임워크 플로우 및 멀티 스레드 동작 분석

이 문서는 `src/framework`에 구현된 파일 처리용 스레드 풀 프레임워크의 실행 흐름과 멀티 스레드 동작 방식을 코드 기준으로 정리한다. 예제 진입점은 `application.main.PingTest`와 `application.main.SimpleFileParse`이며, 두 예제 모두 같은 프레임워크에 `DataRepository`와 `ThreadWorker` 구현체를 주입해서 실행한다.

## 1. 전체 구조

프레임워크는 크게 다음 역할로 나뉜다.

| 구성 요소 | 위치 | 역할 |
| --- | --- | --- |
| `ThreadPoolFramework` | `src/framework/ThreadPoolFramework.java` | 스케줄러 스레드, 작업 스레드 풀, 공유 작업 큐를 생성하고 전체 수명주기를 관리한다. |
| `ThreadRunner` | `src/framework/ThreadRunner.java` | 실제 Java `Thread` 구현체다. 공유 큐에서 `ThreadJob`을 꺼내 `ThreadWorker.serve()`를 실행한다. |
| `ThreadData` | `src/framework/ThreadData.java` | `LinkedBlockingQueue<ThreadJob>`를 상속한 작업 전달 큐다. 현재 구현에서는 모든 워커가 같은 큐를 공유한다. |
| `ThreadJob` | `src/framework/ThreadJob.java` | 작업 인덱스와 처리 대상 데이터를 담는 작업 단위 객체다. 실행 직전 워커 스레드 인덱스가 설정된다. |
| `DataRepository` | `src/framework/DataRepository.java` | 입력 데이터를 읽는 전략 인터페이스다. 파일, DB, 네트워크 등으로 확장 가능하게 설계되어 있다. |
| `ThreadWorker` | `src/framework/ThreadWorker.java` | 작업 처리 전략 인터페이스다. `serve(ThreadJob)`에서 실제 업무 로직을 수행한다. |

핵심 설계는 "입력 읽기"와 "작업 처리"를 인터페이스로 분리하고, `ThreadPoolFramework`가 공유 `BlockingQueue`를 통해 둘 사이를 멀티 스레드로 연결하는 방식이다.

```text
main thread
  └─ DataRepository.init()
  └─ new ThreadPoolFramework(worker, repository, N)
       ├─ shared ThreadData queue
       │    ├─ worker thread 0 poll()
       │    ├─ worker thread 1 poll()
       │    └─ worker thread N-1 poll()
       └─ scheduler thread
            ├─ repository.getData()
            ├─ ThreadJob 생성
            ├─ shared queue에 put()
            └─ EOF 또는 예외 후 schedulerFinished 설정
```

## 2. 생성 및 시작 흐름

`PingTest`는 `CRStringParser`와 `PingThreadWorker`를 생성하고, `SimpleFileParse`는 `MMapStringParser`와 `JeongSanFileWorker`를 생성한다. 이후 공통적으로 다음 흐름을 탄다.

1. `DataRepository.init(filePath)`로 입력 소스를 연다.
2. `new ThreadPoolFramework(threadWorker, dataRepo, maxThreadCount)`를 호출한다.
3. 생성자에서 인자 유효성을 검증한다.
4. `new ThreadData(maxThreadCount * 2)`로 제한 용량의 공유 작업 큐를 만든다.
5. `createThreadPool(i)`를 반복 호출해 `ThreadRunner`를 `maxThreadCount`개 생성하고 시작한다.
6. 모든 `ThreadRunner`에는 같은 `ThreadData` 큐와 같은 `ThreadWorker` 구현체가 전달된다.
7. 1초 대기 후 스케줄러 스레드를 생성하고 시작한다.

작업 스레드와 스케줄러 스레드는 모두 `setDaemon(true)`로 데몬 스레드로 설정된다. 예제에서는 `finishingJob()`을 호출해 스케줄러 종료와 워커 종료를 `join()`으로 기다리므로 정상 실행 경로에서는 작업이 중간에 끊기지 않는다.

## 3. 스케줄러 스레드 흐름

스케줄러는 `ThreadPoolFramework.threadScheduler()`에서 실행된다.

```text
try
  for (long i = 0;; i++)
    readData = dataRepo.getData()
    readData == null 이면 EOF 처리 후 loop 종료
    readData.toString()이 빈 문자열이 아니면 pushJob(i, readString)
catch
  schedulerException 저장
finally
  schedulerFinished = true
```

세부 동작은 다음과 같다.

1. `dataRepo.getData()`를 호출해 입력 데이터를 한 건 읽는다.
2. 반환값이 `null`이면 EOF로 판단하고 스케줄러 루프를 종료한다.
3. 읽은 데이터가 빈 문자열이 아니면 `pushJob(jobIndex, data)`로 공유 큐에 전달한다.
4. `pushJob()`은 유휴 워커를 직접 찾지 않고 `jobQueue.put(threadJob)`만 수행한다.
5. 큐가 가득 차면 `put()`이 블로킹되어 워커가 작업을 소비할 때까지 기다린다. 이 동작이 자연스러운 backpressure 역할을 한다.
6. EOF를 만나면 `inputFinished = true`로 설정한다.
7. EOF 또는 예외 후 `finally`에서 `schedulerFinished = true`로 설정한다.

`inputFinished`, `schedulerFinished`, `workerFinished`는 `AtomicBoolean`으로 선언되어 있어 입력 EOF, 스케줄러 종료, 워커 전체 종료 상태를 구분한다. `schedulerException`은 `volatile`로 선언되어 있어 메인 스레드와 스케줄러 스레드 사이에서 값 변경이 관찰되도록 의도되어 있다.

## 4. 작업 할당 방식

작업 할당은 `ThreadPoolFramework.pushJob()`에서 수행된다.

```text
pushJob(jobIndex, obj)
  threadJob = new ThreadJob(-1, jobIndex, obj)
  jobQueue.put(threadJob)
```

이전처럼 워커 리스트를 순회하거나 `ThreadRunner`의 ready 상태를 확인하지 않는다. 모든 워커는 같은 큐에서 `poll()`을 호출하며, 먼저 작업을 소비할 수 있는 워커가 다음 `ThreadJob`을 가져간다.

현재 큐 용량은 `maxThreadCount * 2`다. 입력 읽기 속도가 처리 속도보다 빠를 때 스케줄러가 작업을 무제한 적재하지 않도록 제한하고, 큐가 가득 차면 `put()`에서 대기한다. 이 구조는 큐가 스케줄러와 워커 사이의 동기화 지점이 되게 한다.

## 5. 워커 스레드 동작

각 `ThreadRunner`는 생성 직후 시작되고, 다음 루프를 돈다.

```text
while (true)
  threadJob = jobQueue.poll(100ms)
  threadJob이 없고 schedulerFinished이며 queue가 비어 있으면 종료
  threadJob.threadIndex = this.threadIndex
  threadWorker.serve(threadJob)
  Thread.yield()
```

동작 순서는 다음과 같다.

1. 워커는 공유 `ThreadData` 큐에서 `poll(100ms)`를 호출한다.
2. 큐가 비어 있으면 짧게 대기한 뒤 스케줄러 완료 여부와 큐 상태를 확인한다.
3. 일반 작업을 받으면 자기 `threadIndex`를 `ThreadJob`에 설정한다.
4. 주입받은 `ThreadWorker` 구현체의 `serve(threadJob)`을 호출한다.
5. 작업이 없고 `schedulerFinished == true`이며 큐가 비어 있으면 `run()`을 종료한다.
6. `InterruptedException`을 받으면 워커 스레드도 종료한다.
7. `serve()`에서 예외가 발생하면 공유 `AtomicReference<Exception>`에 첫 번째 예외를 저장하고 다음 작업을 계속 대기한다.

`ThreadRunner.status`, `isReady()`, `setBusy()`, `setReady()` 기반 상태 관리는 제거되었다. 작업 가능 여부는 큐의 블로킹 동작으로 자연스럽게 처리된다.

## 6. 종료 흐름

종료 처리는 `ThreadPoolFramework.finishingJob()`이 담당한다.

```text
finishingJob()
  schedulerThread.join()
  waitAllThread()
    각 worker thread join()
  schedulerException 있으면 throw
  workerException 있으면 throw
  dataRepo.close()
```

스케줄러는 EOF를 만나면 `inputFinished`를 설정하고, EOF 또는 예외 후 `finally`에서 `schedulerFinished`를 설정한다. 각 워커는 큐에서 더 이상 작업을 가져오지 못하고 `schedulerFinished`가 설정되어 있으며 큐가 비어 있으면 정상적으로 종료한다. `waitAllThread()`는 `THREAD_POOL`에 보관된 모든 `ThreadRunner`를 꺼내 `join()`으로 실제 스레드 종료를 기다린 뒤 `workerFinished`를 설정한다.

이 방식은 특정 워커의 ready 상태나 리스트 순서에 의존하지 않는다. 작업이 남아 있으면 워커들은 큐의 일반 작업을 계속 소비하고, 큐가 비어야 종료 조건을 만족한다. 따라서 종료 제어 신호가 업무 데이터 큐에 섞이지 않는다.

`finally` 블록에서 `dataRepo.close()`를 호출하므로, 정상 종료와 예외 종료 모두 repository 자원 반납을 시도한다.

## 7. 예제별 적용 흐름

### 7.1 `PingTest`

`PingTest`는 줄 단위 파일을 읽어 각 IP에 ping을 수행한다.

```text
fixtures/ping_100.txt
  └─ CRStringParser.getData()
       └─ BufferedReader.readLine()
            └─ ThreadPoolFramework.pushJob()
                 └─ shared queue
                      └─ PingThreadWorker.serve()
                           └─ ProcessBuilder("ping", ...)
```

`PingThreadWorker`는 모든 워커 스레드가 공유하는 단일 객체다. 따라서 `successCount`, `failCount`, `totalCount`는 여러 스레드에서 동시에 접근된다. 이 구현체는 카운트 증가 메서드와 `getSummary()`를 `synchronized`로 선언해 공유 카운터의 동시 접근을 보호한다.

`PingTest`는 프레임워크 생성 후 바로 `finishingJob()`을 호출한다. 이 호출이 EOF와 모든 작업 완료까지 블로킹하므로, 이후 출력되는 `threadWorker.getSummary()`는 모든 ping 작업이 반영된 결과가 된다.

### 7.2 `SimpleFileParse`

`SimpleFileParse`는 메모리 맵 파일에서 10바이트 단위로 데이터를 읽어 출력한다.

```text
fixtures/test2.txt
  └─ MMapStringParser.getData()
       └─ MappedByteBuffer.get(byte[10])
            └─ ThreadPoolFramework.pushJob()
                 └─ shared queue
                      └─ JeongSanFileWorker.serve()
                           └─ 콘솔 출력
```

`SimpleFileParse`는 먼저 `getCheckEOF()`가 `true`가 될 때까지 메인 스레드에서 100ms 단위로 기다린 뒤 `finishingJob()`을 호출한다. 현재 `getCheckEOF()`는 호환을 위해 유지된 메서드이며 스케줄러가 더 이상 작업을 추가하지 않는 상태를 반환한다. 실제 워커 완료와 종료 정리는 `finishingJob()`에서 수행한다.

## 8. 멀티 스레드 동작 요약

이 프레임워크의 동시성 모델은 다음과 같다.

| 스레드 | 개수 | 역할 | 주요 대기 지점 |
| --- | --- | --- | --- |
| 메인 스레드 | 1 | repository 초기화, 프레임워크 생성, 종료 대기, 요약 출력 | `schedulerThread.join()`, worker `join()` |
| 스케줄러 스레드 | 1 | 입력 데이터를 순차적으로 읽고 공유 큐에 적재 | 큐가 가득 찼을 때 `LinkedBlockingQueue.put()` |
| 워커 스레드 | `maxThreadCount` | 공유 큐에서 작업을 꺼내 `ThreadWorker.serve()` 실행 | 큐가 비어 있을 때 `LinkedBlockingQueue.poll(100ms)` |

작업 처리 순서는 입력 순서와 완전히 같다고 보장되지 않는다. 스케줄러는 `jobIndex`를 증가시키며 순차 적재하지만, 실제 완료 순서는 각 워커의 처리 시간에 따라 달라진다. 예를 들어 ping 작업은 네트워크 응답 시간이나 프로세스 실행 시간에 따라 먼저 적재된 작업이 나중에 끝날 수 있다.

## 9. 동기화 및 공유 상태

프레임워크의 주요 동기화 지점은 다음과 같다.

| 대상 | 방식 | 목적 |
| --- | --- | --- |
| `inputFinished` | `AtomicBoolean` | 스케줄러가 입력 EOF를 만났는지 나타낸다. |
| `schedulerFinished` | `AtomicBoolean` | 스케줄러가 더 이상 작업을 추가하지 않는지 나타낸다. 워커 종료 조건에도 사용된다. |
| `workerFinished` | `AtomicBoolean` | 모든 워커 스레드가 종료되었는지 나타낸다. |
| `schedulerException` | `volatile` | 스케줄러에서 발생한 예외를 종료 처리 시 확인한다. |
| `workerException` | `AtomicReference<Exception>` | 여러 워커 중 처음 발생한 예외를 스레드 안전하게 저장한다. |
| `THREAD_POOL` | `synchronized (THREAD_POOL)` | 워커 리스트 추가와 제거를 보호한다. |
| `ThreadData` | 제한 용량 `LinkedBlockingQueue` | 스케줄러와 워커 사이의 안전한 작업 전달, 대기, backpressure를 담당한다. |
| `PingThreadWorker` 카운터 | `synchronized` 메서드 | 여러 워커가 공유하는 성공/실패/전체 카운트를 보호한다. |

`ThreadWorker` 구현체는 워커마다 새로 만들어지지 않고 하나의 객체가 모든 `ThreadRunner`에 공유된다. 따라서 `ThreadWorker` 내부에 가변 상태를 둘 때는 `PingThreadWorker`처럼 동기화가 필요하다.

## 10. 예외 처리 방식

스케줄러 예외는 `ThreadPoolFramework.threadScheduler()`에서 잡아 `schedulerException`에 저장한다. 이후 `finally`에서 `schedulerFinished`를 설정해 워커가 큐 잔여 작업을 처리한 뒤 종료 조건을 판단할 수 있게 한다. `finishingJob()`은 스케줄러와 워커 종료를 기다린 뒤 이 예외를 다시 던진다.

워커 예외는 `ThreadRunner.run()`에서 잡아 공유 `AtomicReference<Exception>`에 저장한다. `compareAndSet(null, e)`를 사용하므로 여러 워커에서 예외가 발생해도 첫 번째 예외만 보존된다. 이후 `finishingJob()`에서 `workerException.get()`을 확인하고 호출자에게 다시 던진다.

단, `ThreadWorker.serve()`가 예외를 내부에서 삼키면 프레임워크는 실패를 알 수 없다. 예를 들어 `PingThreadWorker`는 ping 실행 중 발생한 예외를 `catch(Exception e){}`에서 무시하고 실패 카운트로 처리한다. 이 경우 프레임워크 레벨 예외가 아니라 업무 실패로 남는다.

## 11. 현재 구현의 주의점

1. 스케줄러와 워커는 여전히 데몬 스레드다. 정상 진입점처럼 `finishingJob()`을 호출하면 `join()`으로 완료를 기다리지만, 호출을 누락하면 메인 종료 시 작업이 중간에 끊길 수 있다.
2. `ThreadWorker` 구현체는 공유 객체다. 내부 상태가 있으면 반드시 동기화나 스레드 안전한 자료구조를 사용해야 한다.
3. 처리 완료 순서는 입력 순서와 다를 수 있다. 순서가 중요한 업무라면 `jobIndex` 기반 정렬 또는 단일 스레드 처리 전략이 필요하다.
4. `DataRepository.getData()`는 스케줄러 스레드 하나에서만 호출된다. 현재 구조에서는 repository 구현체 자체가 멀티 스레드 안전할 필요는 낮지만, 향후 스케줄러를 늘리면 별도 보호가 필요하다.
5. 공유 큐 용량은 `maxThreadCount * 2`로 고정되어 있다. 처리 시간이 매우 길거나 입력 데이터가 매우 큰 업무에서는 처리량과 메모리 사용량을 기준으로 용량을 설정할 수 있게 옵션화하는 방안을 검토할 수 있다.
6. 스케줄러 예외가 발생한 경우에도 이미 큐에 들어간 작업은 처리된 뒤 종료될 수 있다. 즉 예외 발생 즉시 모든 작업을 중단하는 fail-fast 구조는 아니다.

## 12. 핵심 결론

이 서비스의 프레임워크는 Java 표준 `Thread`와 제한 용량 `LinkedBlockingQueue`를 이용해 직접 만든 단순한 스레드 풀이다. 입력 데이터는 스케줄러 스레드가 순차적으로 읽고 공유 큐에 적재하며, 여러 워커 스레드가 같은 큐에서 경쟁적으로 작업을 가져가 `ThreadWorker.serve()`를 실행한다.

즉, 병렬화되는 구간은 `DataRepository.getData()`가 아니라 `ThreadWorker.serve()` 실행 구간이다. 파일 읽기는 단일 스케줄러에서 순차 수행되고, 읽힌 각 레코드의 후속 처리가 여러 워커 스레드에서 동시에 실행되는 구조다. 종료는 스케줄러 완료 상태와 큐 잔여 작업 여부를 기준으로 워커가 스스로 종료하고, `join()`으로 실제 스레드 종료를 기다리는 방식으로 처리된다.
