# Goguma Evangelism Game

전도 대상자를 “고구마”로 등록하고, 말씀·기도·연락·만남·권유 행동을 기록하며 성장시키는 게이미피케이션 웹앱입니다. 교회 청년부/소그룹에서 지속적인 전도 실천을 돕기 위한 개인 미션, 주간 미션, 성장 히스토리, 커뮤니티 기능을 포함합니다.

## 프로젝트 개요

- 목적: 전도 대상자 관리와 전도 실천 습관화를 게임 경험으로 전환
- 형태: Express 기반 서버 렌더리스 싱글 페이지 게임
- 데이터: SQLite + 파일 세션
- UI: HTML/CSS/Vanilla JavaScript 기반 픽셀아트 게임 화면
- 주요 대상: 교회 청년부, 소그룹, 전도팀

## 주요 기능

- 이름/부서 기반 사용자 시작 및 세션 유지
- 전도 대상자 “고구마” 등록
- 대상자별 관계, 나이, 성장 온도 관리
- 행동별 성장 기록
  - 말씀읽기
  - 기도하기
  - 연락&만남
  - 권유하기
  - 게시판 작성
- 행동별 하루 1회 제한
- KST 기준 날짜/주차 계산
- 일일 미션과 주간 미션 보상
- 성장 단계별 캐릭터/전직 패시브 시스템
- 최근 7일 행동 히스토리와 통계
- 출석일 기반 룰렛 보상
- 게시판, 좋아요, 댓글, 이미지 첨부
- 부서별 랭킹
- 입력 검증, rate limit, 기본 보안 헤더 적용

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Node.js, Express |
| Database | SQLite, better-sqlite3 |
| Session | express-session, session-file-store |
| Frontend | HTML, CSS, Vanilla JavaScript |
| Assets | Pixel art images, GIF, MP3 |
| Runtime | npm, Node.js |

## 아키텍처

```text
Browser
  └─ goguma-app.html
      ├─ Pixel Art UI
      ├─ Mission / Growth interaction
      └─ Fetch API

Express Server
  ├─ Static asset serving
  ├─ Session middleware
  ├─ REST API
  ├─ Rate limiter
  └─ Security headers

SQLite
  ├─ users
  ├─ gogumas
  ├─ actions
  ├─ user_activity
  ├─ mission_rewards
  ├─ posts
  ├─ post_likes
  └─ post_comments
```

## 폴더 구조

```text
server.js            # Express 서버, DB 스키마, API 라우팅
goguma-app.html      # 게임 UI와 클라이언트 로직
game_images/         # 픽셀아트 캐릭터, 배경, 이펙트 이미지
game_sounds/         # BGM, 버튼 효과음
package.json         # 실행 스크립트와 의존성
```

## 실행 방법

```bash
npm install
npm start
```

브라우저에서 접속:

```text
http://localhost:3001
```

포트 변경:

```bash
PORT=4000 npm start
```

## 환경 변수

`.env.example`을 참고해 운영 환경에서는 세션 키를 별도로 지정합니다.

```env
PORT=3001
SESSION_SECRET=replace-with-a-long-random-secret
```

## 주요 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/me` | 현재 세션 사용자, 고구마, 미션 상태 조회 |
| POST | `/api/start` | 이름/부서 기반 사용자 시작 |
| POST | `/api/goguma/add` | 전도 대상자 등록 |
| POST | `/api/goguma/grow` | 행동 기록 및 성장 처리 |
| GET | `/api/missions` | 일일/주간 미션 조회 |
| POST | `/api/missions/:key/claim` | 미션 보상 수령 |
| GET | `/api/goguma/:id/history` | 최근 7일 성장 히스토리 |
| GET | `/api/ranking` | 부서/사용자 랭킹 조회 |
| GET | `/api/posts` | 커뮤니티 글 목록 |
| POST | `/api/posts/add` | 커뮤니티 글 작성 |
| POST | `/api/posts/:id/like` | 좋아요 토글 |
| POST | `/api/posts/:id/comments` | 댓글 작성 |

## 구현 포인트

- 단순 CRUD를 넘어 날짜, 주차, 행동 제한, 보상 수령 여부를 서버에서 일관되게 계산
- SQLite unique constraint로 행동 중복 기록과 미션 중복 보상을 방지
- 입력 길이 제한과 문자열 정규화로 게시판/댓글/이름 입력 안정화
- API 성격별 rate limit을 분리해 반복 클릭과 커뮤니티 남용을 제어
- 성장 단계별 행동 점수 기반 전직/캐릭터 변화를 구현
- KST 기준 날짜 계산으로 국내 사용자 경험에 맞춘 일일 리셋 제공
- 픽셀아트 UI, 사운드, 배경 레이어를 활용해 도메인에 맞는 게임 감성 구현

## 저장소 운영 메모

- `goguma.db`와 `sessions/`는 실행 중 생성되는 로컬 데이터이므로 Git에 포함하지 않습니다.
- 실제 배포 환경에서는 `SESSION_SECRET`을 반드시 별도 환경 변수로 지정해야 합니다.
- SQLite 파일 기반 구조는 소규모 그룹 운영에 적합하며, 다중 인스턴스 배포 시 PostgreSQL 등 외부 DB로 이전할 수 있습니다.
