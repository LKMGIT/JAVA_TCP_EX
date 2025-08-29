# Java 멀티 채팅 서버

간단한 **라인 기반(line‑based) TCP 채팅 서버**입니다. 여러 클라이언트가 동시에 접속할 수 있고,
**스레드 풀(ExecutorService)** 로 클라이언트 핸들러를 관리합니다. 닉네임 등록, 인원 조회, 메시지 방송을
지원합니다.

> 본 README는 `Server`와 `ClientHandler`(Runnable) 기반의 구현을 기준으로 작성되었습니다.

---

## 🔍 주요 기능

* **다중 접속 처리**: `Executors.newCachedThreadPool()` 기반 워커 스레드가 `Runnable` 작업 실행
* **닉네임 등록 & 중복 거부**: 최초 1줄에 닉네임 전송, 중복 시 접속 거부
* **명령어 지원**

  * `/who` : 현재 접속자 수와 닉네임 목록
  * `/text` : 한 줄 메시지를 입력받아 **보낸 사람 제외** 전체에게 방송
  * `/quit` : 정상 종료
* **방송(broadcast)** / **보낸 사람 제외 방송(broadcastExcept)**
* **출력 오류 감지**: `PrintWriter.checkError()`로 끊긴 소켓 감지 및 정리
* **우아한 종료**: `Runtime.getRuntime().addShutdownHook(...)` 이용, 소켓/스레드 정리
* **동시성 안전 컬렉션**: `CopyOnWriteArrayList`로 접속자/핸들러 관리

---

## 🧱 아키텍처 개요

```
[Client(n)] --TCP--> [ServerSocket.accept] --> [ClientHandler (Runnable)]
                                           \-> 스레드 풀(워커)에서 run()
```

* `Server` : 포트 바인딩, `accept()` 루프, 핸들러 생성 및 스레드 풀 제출
* `ClientHandler` : 각 클라이언트와 I/O 처리(UTF‑8), 명령 해석, 방송 참여
* 공유 상태

  * `NAMES: List<String>` : 현재 접속 닉네임
  * `CLIENTS: List<ClientHandler>` : 현재 접속 핸들러

---

## 🔌 프로토콜 & 메시지 규약 (라인 기반)

* **인코딩**: UTF‑8
* **클라이언트 → 서버**

  1. 접속 직후 첫 줄에 **닉네임** 전송 (빈 값/중복 금지)
  2. 이후 명령/텍스트 상호작용
* **서버 → 클라이언트**

  * 환영 메시지, 방송 메시지, 명령 결과 문자열을 **개행으로 구분**해 전송

### 지원 명령

* `/who` — 접속자 수와 목록 반환
* `/text` — 서버가 "메시지를 입력하세요:" 프롬프트 후, **다음 한 줄**을 전체 방송(보낸 사람 제외)
* `/quit` — 연결 종료

---

## ⚙️ 요구 사항

* JDK 17+ (권장)
* 터미널(netcat/nc, telnet 등) 또는 별도 클라이언트 프로그램

---

## ▶️ 빌드 & 실행

### 1) 순수 javac/java

```bash
# 루트에서 (패키지 구조 유지)
javac -encoding UTF-8 August/day25/Multi/Server.java
java August.day25.Multi.Server
```

기본 포트는 `5000`입니다. 변경하려면 `Server.PORT` 상수를 수정하세요.
---

> **중요**: 접속 직후 첫 줄은 반드시 닉네임이어야 합니다.

---

## 👋 입·퇴장 방송 메시지

* 입장: `"[서버] <name>님 입장. 현재 <N>명"`
* 퇴장: `"[서버] <name>님 퇴장. 현재 <N>명"`

---

## 📝 로그 예시

```
[서버] 서버 시작
[서버] 클라이언트와 연결되었습니다.
[서버] 수신: /who
[서버] 수신: /text
[서버] 수신: /quit
```

---

## 🚧 한계 & TODO

* [ ] 개인 메시지(e.g. `/w <name> <msg>`)
* [ ] 채팅방/룸(e.g. `/join #room`)
* [ ] 지속성(채팅 로그 저장)
* [ ] 인증/권한(관리자 명령)
* [ ] 하트비트/핑‑퐁으로 죽은 연결 조기 감지
* [ ] 비동기 로그 & 구조화 로깅(SLF4J 등)

---
