# 저장소 가이드라인

## 프로젝트 구조 및 모듈 구성

이 저장소는 Eclipse Java 프로젝트 형식의 작은 Java 파일 처리 프로젝트입니다. 소스 파일은 `src/`에 있고, 컴파일된 `.class` 출력은 `bin/`에 생성됩니다.

- `src/framework/`: `ThreadPoolFramework`, `ThreadRunner`, `ThreadWorker`, `ThreadJob`, `DataRepository` 등 재사용 가능한 스레드 풀 프레임워크 클래스.
- `src/application/main/`: `PingTest`, `SimpleFileParse` 같은 실행 가능한 예제.
- `src/application/repository/`: `CRStringParser`, `MMapStringParser` 같은 파일/문자열 파서 구현체.
- `src/application/worker/`: 파싱된 레코드를 처리하는 워커 구현체.
- 루트의 `*.txt` 파일: 예제 프로그램에서 사용하는 샘플 입력 데이터.

`bin/`을 원본 소스로 취급하지 마세요. 변경은 `src/`에 적용하고, 필요할 때 컴파일 출력을 다시 생성하세요.

## 빌드, 테스트 및 개발 명령

모든 Java 소스 컴파일:

```sh
javac -encoding UTF-8 -d bin $(find src -name '*.java')
```

ping 예제 실행:

```sh
java -cp bin application.main.PingTest
```

간단한 파일 파서 예제 실행:

```sh
java -cp bin application.main.SimpleFileParse
```

이 프로젝트에는 `.project`와 `.classpath`가 포함되어 있으므로 Eclipse에서 기존 Java 프로젝트로 가져올 수 있습니다.

## 코딩 스타일 및 명명 규칙

Java 패키지명은 기존 디렉터리 구조와 맞추세요: `framework`, `application.main`, `application.repository`, `application.worker`. 클래스와 인터페이스 이름은 `PascalCase`, 메서드와 지역 변수는 `camelCase`를 사용합니다.

현재 코드베이스는 들여쓰기에 탭을 사용하고, `if(condition){` 형태의 K&R 스타일 중괄호를 사용합니다. 주변 파일과 일관되게 수정하세요. 여러 파일에 한글 주석과 샘플 파일명이 포함되어 있으므로 UTF-8 인코딩을 유지하세요.

## 테스트 지침

현재 자동화 테스트 프레임워크는 설정되어 있지 않습니다. 동작을 변경하는 경우 테스트 프레임워크를 도입해 집중적인 테스트를 추가하거나, 위 실행 예제로 수행한 수동 검증 내용을 기록하세요. `test.txt`, `test2.txt`, `ping_100.txt`와 비슷한 작은 fixture 파일을 사용하세요.

스레드 스케줄링, repository 파싱, 워커 동작을 변경할 때는 두 예제 진입점을 모두 확인하고, EOF 처리, 워커 완료, 요약 출력이 콘솔에 올바르게 표시되는지 점검하세요.

## 커밋 및 Pull Request 지침

이 체크아웃에서는 읽을 수 있는 Git 히스토리가 노출되지 않아 저장소 고유의 커밋 규칙을 확인할 수 없습니다. `Fix parser EOF handling`, `Add worker validation`처럼 간결한 명령형 제목을 사용하세요.

Pull Request에는 짧은 설명, 영향을 받은 패키지, 실행한 수동 테스트 명령, 변경된 샘플 데이터를 포함하세요. 콘솔 출력이나 IDE 설정 변경을 시각적으로 설명해야 할 때만 스크린샷을 첨부하세요.

## 에이전트 전용 지침

생성 파일과 빌드 출력은 소스 변경과 분리하세요. `src/`에서 최소한의 집중된 수정을 선호하고, 관련 없는 포맷 변경은 피하세요. 스레딩 동작이나 하드코딩된 Windows 파일 경로에 영향을 주는 변경은 반드시 명시하세요.

## Commit Message Guidelines

- type 이외 내용은 한글로 작성
- 변경 목적을 명확하게 작성
- 한 커밋에는 하나의 작업만 포함
- 짧고 간결하게 작성

형식:

<type>: <summary>

[optional body]

type:
- feat: 기능 추가
- fix: 버그 수정
- refactor: 리팩토링
- test: 테스트
- docs: 문서
- chore: 기타 작업

예시:

fix: 중복 주문 생성 방지

- 동시 요청 처리를 위해 Redis 락 추가
- 재시도 시간 초과 예외 처리
- 결제 API에만 적용