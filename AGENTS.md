# ScreenLingo 개발 지침

사용자는 이 프로젝트에서 기획부터 Android 초기 버전까지 자율 개발과 정기적인 후속 개발을 요청했다. 높은 품질의 작고 완결된 기능을 검증하며 이어간다.

- 시작 시 `README.md`, `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/DEVELOPMENT.md`, `docs/BACKLOG.md`를 읽는다.
- 기존 Flutter 프로젝트를 유지한다. UI는 Flutter, 캡처/OCR/번역/다른 앱 위 오버레이는 Kotlin으로 구현한다.
- 사용자는 밝은 분위기를 원하며 짙은 초록과 불필요한 부제목을 싫어한다. 현재 테마는 화이트/옅은 회색/파랑 강조다. 어두운 대형 카드·장식용 배지·홍보성 제목/부제목을 다시 추가하지 않는다. 권한/개인정보/상태 안내는 필요한 내용만 짧게 쓴다.
- 사용자 캡처/번역문을 로그, 파일, 서버, 분석 서비스에 기록하지 않는다. 번역 기록 저장도 기본 범위 밖이다. 테스트용으로 작성한 연습 화면만 QA 스크린샷으로 남길 수 있다.
- 현재 ML Kit native SDK의 번역문 Logcat 출력이 debug/release 모두에서 재현된다. 해결 전에는 직접 작성한 연습 문장만 시험하고, 민감한 화면/실제 사용자 데이터/외부 배포는 사용하지 않는다. 이 문제를 백로그 최우선으로 처리한다.
- 0.1.1은 이 SDK 문제에 공개 NDK 프로세스 로그 콜백을 적용했다. 최소 지원은 Android 11(API 30)로 조정했다. `docs/SECURITY.md`와 `scripts/verify-privacy.ps1`을 기준으로 보호를 유지하고, API 30/arm64 검증 전에는 위 샘플 전용 제한을 해제하지 않는다. SDK/NDK 변경 때는 debug와 실제 최적화 release 로그 회귀를 모두 확인한다.
- API 30 x86_64 경계 검증도 완료했다(`docs/QA_ENVIRONMENT.md`). ARM64 실기기는 아직 없어 샘플 전용 제한을 유지한다. 별도 `ScreenLingo_API30` AVD는 D: QA 폴더에 있고 `scripts/api30-qa.ps1`로 실행/종료한다. 기존 `emulator-5554`를 종료하지 않는다. 동일 APK의 OS 비교에는 source/보호 라이브러리 검사 후 `-SkipBuild`를 사용해 불필요한 재빌드를 줄인다.
- 매 화면 공유 세션마다 Android 동의 절차를 유지한다. 세션 토큰을 저장·재사용하지 않는다. 서비스는 `START_NOT_STICKY`, 자동 재시작 금지.
- 모의 UI와 실제 결과를 혼동시키지 않는다. 측정하지 않은 속도/정확도를 제품에서 주장하지 않는다.
- 모델 다운로드 이외 번역 네트워크 API나 유료 서비스를 임의 도입하지 않는다. Google Translate 공식 출처 배지를 번역 결과 옆에 유지한다.
- 기존 사용자 변경을 보존한다. 요청 없이 커밋/푸시/스토어 배포/서명 키 생성은 하지 않는다.
- 기능 수정에 맞는 분석·테스트·빌드를 실행하고 결과 및 미검증 항목을 DEVELOPMENT/BACKLOG에 기록한다. 통과한 검사를 변경 없이 반복할 필요는 없다.
- 초기 로컬 작업본은 Git 저장소가 아니고 게시용 clone은 별도다. `git status` 실패를 근거로 파일을 복원/초기화하지 않는다. GitHub 체크아웃에서는 실제 Git 상태를 먼저 확인하고 기존 변경을 보존한다.
- Windows에서 Flutter SDK는 `C:\Develop\flutter`, Android SDK는 `C:\Users\hanin\AppData\Local\Android\sdk`, JDK는 Android Studio의 `jbr`에 있다. PATH가 없어도 절대 경로로 진행한다.

정기 개발은 이 채팅의 한 시간 간격 heartbeat에서 이어간다. 다음 실행은 백로그 상단의 미완료 항목부터 진행하되 같은 장애를 반복 확인하는 데 실행 전체를 사용하지 않는다.
