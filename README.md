# FileProcessing

Java로 작성된 파일 처리 및 스레드 풀 예제 프로젝트입니다. 파일에서 데이터를 읽어 `ThreadPoolFramework`가 작업 스레드에 분배하고, 각 `ThreadWorker` 구현체가 레코드를 처리하는 구조입니다.

## 프로젝트 구조

```text
.
├── src/
│   ├── framework/              # 재사용 가능한 스레드 풀 프레임워크
│   └── application/
│       ├── main/               # 실행 예제
│       ├── repository/         # 파일/문자열 파서
│       └── worker/             # 레코드 처리 워커
├── bin/                        # 컴파일 출력
├── ping_100.txt                # PingTest 샘플 입력
├── ping_700.txt                # PingTest 샘플 입력
├── test.txt                    # 파일 파서 샘플 입력
└── test2.txt                   # 파일 파서 샘플 입력
```

## 요구사항

- JDK 21 이상
- WSL, Linux, macOS, Windows PowerShell 등 `javac`와 `java`를 실행할 수 있는 환경

설치 확인:

```sh
java -version
javac -version
```

## 빌드

프로젝트 루트에서 모든 Java 소스를 컴파일합니다.

```sh
javac -encoding UTF-8 -d bin $(find src -name '*.java')
```

컴파일 결과는 `bin/` 디렉터리에 생성됩니다. `bin/`은 빌드 출력물이므로 소스처럼 직접 수정하지 않습니다.

## 실행

간단한 파일 파서 예제:

```sh
java -cp bin application.main.SimpleFileParse
```

Ping 예제:

```sh
java -cp bin application.main.PingTest
```

`PingTest`는 입력 파일 경로와 스레드 개수를 인자로 받을 수 있습니다.

```sh
java -cp bin application.main.PingTest ping_100.txt 10
```

## 주요 클래스

- `framework.ThreadPoolFramework`: 작업 스레드 생성, 데이터 스케줄링, 종료 대기를 담당합니다.
- `framework.DataRepository`: 입력 데이터를 읽는 저장소 인터페이스입니다.
- `framework.ThreadWorker`: 작업 처리 로직을 정의하는 워커 인터페이스입니다.
- `application.repository.CRStringParser`: 줄 단위로 파일을 읽습니다.
- `application.repository.MMapStringParser`: 메모리 매핑 방식으로 파일을 읽습니다.
- `application.worker.PingThreadWorker`: 입력 IP에 대해 ping 테스트를 수행합니다.
- `application.worker.JeongSanFileWorker`: 고정 길이 문자열 샘플을 처리합니다.

## 개발 메모

- 소스 파일은 UTF-8 인코딩을 사용합니다.
- Eclipse 프로젝트 파일인 `.project`, `.classpath`가 포함되어 있어 Eclipse에서 기존 Java 프로젝트로 가져올 수 있습니다.
- 현재 자동화 테스트 프레임워크는 없습니다. 동작 변경 후에는 `SimpleFileParse`, `PingTest`를 직접 실행해 EOF 처리, 워커 종료, 요약 출력이 정상인지 확인하세요.
