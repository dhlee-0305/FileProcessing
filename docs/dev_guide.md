# 멀티 스레드 프레임워크 개발 가이드

이 문서는 `src/framework`의 멀티 스레드 프레임워크를 사용해 새로운 기능 또는 서비스를 구현할 때 참고할 개발 가이드다. 현재 프레임워크는 입력 데이터를 순차적으로 읽는 `DataRepository`, 실제 업무를 병렬 처리하는 `ThreadWorker`, 두 구현체를 연결하는 `ThreadPoolFramework`로 구성된다.

## 1. 기본 동작 모델

새 서비스를 만들 때 이해해야 할 핵심 흐름은 다음과 같다.

```text
main
  ├─ DataRepository 구현체 생성 및 init()
  ├─ ThreadWorker 구현체 생성
  ├─ new ThreadPoolFramework(worker, repository, threadCount)
  ├─ finishingJob()으로 모든 작업 완료 대기
  └─ worker.getSummary()로 결과 확인
```

프레임워크 내부에서는 스케줄러 스레드 1개가 `DataRepository.getData()`를 반복 호출한다. 읽은 데이터가 있으면 `ThreadJob`으로 감싸 공유 큐에 넣고, 여러 워커 스레드가 같은 큐에서 작업을 가져가 `ThreadWorker.serve()`를 실행한다.

즉, 병렬화되는 구간은 `DataRepository.getData()`가 아니라 `ThreadWorker.serve()`다. 입력 읽기는 단일 스레드에서 순차 수행되고, 읽힌 각 레코드의 처리만 여러 스레드에서 동시에 실행된다.

## 2. 새 서비스 구현 절차

새 기능은 보통 다음 3개 클래스를 추가하면 된다.

1. `DataRepository` 구현체: 처리 대상 데이터를 한 건씩 읽는다.
2. `ThreadWorker` 구현체: 한 건의 데이터를 실제로 처리한다.
3. `main` 진입점: repository와 worker를 조립하고 프레임워크를 실행한다.

권장 패키지 위치는 다음과 같다.

| 구현 대상 | 권장 위치 | 예시 |
| --- | --- | --- |
| 실행 클래스 | `src/application/main/` | `OrderFileProcess.java` |
| 입력 데이터 조회 | `src/application/repository/` | `OrderFileRepository.java` |
| 업무 처리 | `src/application/worker/` | `OrderWorker.java` |

## 3. `DataRepository` 구현 방법

`DataRepository`는 입력 소스를 초기화하고 데이터를 한 건씩 반환한다. 더 이상 읽을 데이터가 없으면 반드시 `null`을 반환해야 한다. `null`은 프레임워크에서 EOF 신호로 사용된다.

```java
package application.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import framework.DataRepository;

public class SampleLineRepository implements DataRepository {
	private BufferedReader bufferReader = null;

	public void init(Object repoInfo) throws Exception {
		File file = new File(repoInfo.toString());
		bufferReader = new BufferedReader(new FileReader(file));
	}

	public Object getData() throws Exception {
		String line = bufferReader.readLine();
		if (line == null) {
			close();
		}
		return line;
	}

	public void close() {
		try {
			if (bufferReader != null) {
				bufferReader.close();
				bufferReader = null;
			}
		} catch (Exception e) {
		}
	}
}
```

구현 시 주의할 점:

1. `getData()`는 EOF에서 `null`을 반환해야 한다.
2. 빈 문자열은 프레임워크에서 작업으로 전달되지 않는다.
3. `getData()`에서 예외가 발생하면 스케줄러 예외로 저장되고, `finishingJob()`에서 다시 전달된다.
4. 자원은 EOF 시점과 `close()`에서 모두 안전하게 정리되도록 작성한다.
5. 현재 구조에서는 `getData()`가 스케줄러 스레드 하나에서만 호출되므로 repository 자체는 보통 멀티 스레드 안전할 필요가 낮다.

## 4. `ThreadWorker` 구현 방법

`ThreadWorker`는 실제 업무 로직을 담당한다. 모든 워커 스레드가 같은 `ThreadWorker` 인스턴스를 공유하므로, 내부에 카운터나 컬렉션 같은 가변 상태를 두면 반드시 동기화해야 한다.

```java
package application.worker;

import framework.ThreadJob;
import framework.ThreadWorker;

public class SampleWorker implements ThreadWorker {
	private long totalCount = 0;
	private long successCount = 0;
	private long failCount = 0;

	public void serve(ThreadJob threadJob) {
		int threadIndex = threadJob.getThreadIndex();
		long jobIndex = threadJob.getJobIndex();
		String data = threadJob.getObject().toString();

		try {
			// 실제 업무 처리
			System.out.println("T" + String.format("%03d", threadIndex)
					+ " (" + String.format("%03d", jobIndex) + ") " + data);
			addSuccessCount();
		} catch (Exception e) {
			addFailCount();
			throw new RuntimeException(e);
		}
	}

	private synchronized void addSuccessCount() {
		successCount++;
		totalCount++;
	}

	private synchronized void addFailCount() {
		failCount++;
		totalCount++;
	}

	public synchronized String getSummary() {
		return "Total:" + totalCount + ", Success:" + successCount + ", Fail:" + failCount;
	}
}
```

구현 시 주의할 점:

1. `ThreadJob.getObject()`의 실제 타입은 repository가 반환한 객체의 `toString()` 결과다. 현재 `ThreadPoolFramework`가 `readData.toString()`을 큐에 넣기 때문이다.
2. `threadIndex`는 작업을 실제로 가져간 워커 스레드 번호다.
3. `jobIndex`는 스케줄러가 입력 데이터를 읽은 순서다. 처리 완료 순서를 의미하지 않는다.
4. 공유 카운터는 `synchronized`, `AtomicLong`, `ConcurrentHashMap` 같은 스레드 안전한 방식으로 관리한다.
5. 업무 실패를 프레임워크 예외로 전달하려면 `serve()`에서 예외를 던진다. 내부에서 예외를 삼키면 프레임워크는 실패를 알 수 없다.

## 5. 실행 클래스 작성 방법

실행 클래스는 repository 초기화, 프레임워크 생성, 종료 대기를 책임진다.

```java
package application.main;

import application.repository.SampleLineRepository;
import application.worker.SampleWorker;
import framework.DataRepository;
import framework.ThreadPoolFramework;
import framework.ThreadWorker;

public class SampleService {
	public static void main(String[] args) {
		String filePath = "fixtures/sample.txt";
		int threadCount = 5;
		ThreadPoolFramework threadPoolFrame = null;

		ThreadWorker worker = new SampleWorker();
		DataRepository repository = new SampleLineRepository();

		try {
			repository.init(filePath);
			threadPoolFrame = new ThreadPoolFramework(worker, repository, threadCount);
			threadPoolFrame.finishingJob();
			System.out.println(worker.getSummary());
		} catch (Exception e) {
			System.out.println("SampleService error:" + e.getMessage());
			try {
				if (threadPoolFrame != null) {
					threadPoolFrame.finishingJob();
				}
			} catch (Exception e2) {
				System.out.println("SampleService finishing error:" + e2.getMessage());
			}
		}
	}
}
```

실행 클래스에서는 `finishingJob()`을 반드시 호출해야 한다. 프레임워크의 스케줄러와 워커는 데몬 스레드로 생성되므로, 메인 스레드가 먼저 종료되면 작업이 중간에 끊길 수 있다.

## 6. 스레드 수 선택 기준

`ThreadPoolFramework`의 세 번째 인자는 워커 스레드 개수다. 내부 공유 큐 용량은 `maxThreadCount * 2`로 생성된다.

스레드 수는 작업 성격에 따라 다르게 잡는다.

| 작업 성격 | 권장 방향 |
| --- | --- |
| CPU 계산 중심 | CPU 코어 수와 비슷하게 시작한다. |
| 파일 또는 네트워크 I/O 중심 | 코어 수보다 크게 잡을 수 있다. 응답 대기 시간이 길수록 더 많은 워커가 유리할 수 있다. |
| 외부 API 호출 | 상대 시스템의 rate limit, timeout, 연결 수 제한을 우선 고려한다. |
| DB 작업 | DB 커넥션 풀 크기보다 큰 워커 수는 병목이나 timeout을 만들 수 있다. |

처음에는 작은 fixture로 `5`, `10`, `20` 정도를 비교하고, 처리 시간과 실패율을 함께 확인하는 방식이 현실적이다.

## 7. 처리 순서와 결과 집계

입력 순서와 처리 완료 순서는 다를 수 있다. 예를 들어 `jobIndex=1` 작업이 외부 응답을 오래 기다리면, `jobIndex=2` 작업이 먼저 끝날 수 있다.

순서가 필요한 기능에서는 다음 중 하나를 선택한다.

1. `jobIndex`를 결과에 함께 저장하고 마지막에 정렬한다.
2. 순서가 중요한 구간만 단일 스레드로 처리한다.
3. 병렬 처리 결과를 별도 저장소에 기록하고 후처리 단계에서 순서를 맞춘다.

콘솔 로그도 여러 워커가 동시에 출력하므로 순서가 섞일 수 있다. 로그 순서만으로 입력 처리 순서를 판단하지 말고 `jobIndex`를 기준으로 확인한다.

## 8. 예외 처리 정책

프레임워크는 다음 방식으로 예외를 처리한다.

1. `DataRepository.getData()` 예외는 `schedulerException`에 저장된다.
2. `ThreadWorker.serve()` 예외는 공유 `AtomicReference<Exception>`에 첫 번째 예외만 저장된다.
3. `finishingJob()`은 스케줄러와 워커 종료를 기다린 뒤 저장된 예외를 다시 던진다.
4. 워커 예외가 발생해도 이미 큐에 들어간 작업은 계속 처리될 수 있다. 현재 구조는 즉시 중단하는 fail-fast 방식이 아니다.

업무별로 실패 정책을 정해야 한다.

| 정책 | 구현 방향 |
| --- | --- |
| 일부 실패 허용 | `serve()` 내부에서 예외를 잡고 실패 카운트나 실패 목록에 기록한다. |
| 하나라도 실패하면 최종 실패 | `serve()`에서 예외를 던져 `finishingJob()` 호출자가 알 수 있게 한다. |
| 재시도 필요 | `serve()` 내부에서 제한 횟수만큼 재시도하고, 최종 실패 시 기록하거나 예외를 던진다. |

## 9. 자원 관리

repository와 worker에서 파일, 네트워크, 프로세스, DB 커넥션 같은 자원을 사용할 때는 다음 원칙을 지킨다.

1. repository 입력 자원은 `DataRepository.close()`에서 반납한다.
2. worker 내부에서 매 작업마다 여는 자원은 `serve()`의 `finally`에서 닫는다.
3. 여러 워커가 공유하는 자원은 스레드 안전성 또는 풀링 정책을 확인한다.
4. 외부 프로세스를 실행하면 입력/출력/에러 스트림을 닫고 timeout을 둔다.
5. 장시간 블로킹되는 외부 호출에는 timeout을 명시한다.

`PingThreadWorker`는 `ProcessBuilder`와 `waitFor(timeout)`을 사용하고, finally에서 스트림과 프로세스를 정리하는 예시다.

## 10. 개발 체크리스트

새 서비스를 추가할 때 다음 항목을 확인한다.

1. `DataRepository.getData()`가 EOF에서 `null`을 반환하는가?
2. `ThreadWorker.serve()`가 공유 상태를 스레드 안전하게 갱신하는가?
3. `finishingJob()`을 반드시 호출하는가?
4. `getSummary()`가 모든 작업 완료 후 호출되는가?
5. 외부 자원과 프로세스에 timeout과 close 처리가 있는가?
6. 입력 순서와 처리 완료 순서가 달라도 문제가 없는가?
7. 실패를 예외로 전파할지, 실패 카운트로 처리할지 정했는가?
8. 작은 fixture로 EOF, 빈 줄, 잘못된 데이터, 예외 상황을 확인했는가?

## 11. 빌드 및 실행

전체 Java 소스 컴파일:

```sh
javac -encoding UTF-8 -d bin $(find src -name '*.java')
```

컴파일 경고까지 확인:

```sh
javac -Xlint:all -encoding UTF-8 -d bin $(find src -name '*.java')
```

기존 예제 실행:

```sh
java -cp bin application.main.SimpleFileParse
java -cp bin application.main.PingTest
```

새 실행 클래스를 만들었다면 같은 방식으로 실행한다.

```sh
java -cp bin application.main.SampleService
```

## 12. 권장 검증 시나리오

새 서비스를 구현한 뒤에는 최소한 다음을 확인한다.

1. 입력 파일이 비어 있을 때 정상 종료되는지 확인한다.
2. 입력 파일 마지막 줄에 개행이 없어도 마지막 레코드가 처리되는지 확인한다.
3. 빈 줄이 작업에서 제외되어도 문제가 없는지 확인한다.
4. `threadCount=1`과 다중 스레드 실행 결과가 논리적으로 같은지 확인한다.
5. worker에서 의도적으로 예외를 발생시켰을 때 `finishingJob()`이 예외를 전달하는지 확인한다.
6. 처리 결과 카운트가 입력 레코드 수와 일치하는지 확인한다.
7. 외부 시스템을 호출하는 경우 timeout, 실패율, 재시도 정책이 기대대로 동작하는지 확인한다.

## 13. 구현 예제 참고

현재 저장소의 예제는 다음 용도로 참고할 수 있다.

| 예제 | 참고 포인트 |
| --- | --- |
| `application.main.PingTest` | 인자 처리, 스레드 수 지정, `finishingJob()` 이후 요약 출력 |
| `application.repository.CRStringParser` | 줄 단위 파일 입력 repository 구현 |
| `application.worker.PingThreadWorker` | 공유 카운터 동기화, 외부 프로세스 실행, timeout 처리 |
| `application.main.SimpleFileParse` | 고정 길이 파일 처리 예제 |
| `application.repository.MMapStringParser` | 메모리 맵 기반 파일 입력 |
| `application.worker.JeongSanFileWorker` | 가장 단순한 worker 출력 예제 |
