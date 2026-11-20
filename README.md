# ScreenLingo

Android 화면의 글자를 기기에서 인식·번역하고 다른 앱 위 카드에 표시하는 Flutter/Kotlin 앱입니다.

> **개발 미리보기 — 작성한 연습 문장만 시험하세요.** ARM64 실기기의 로그 보호 검증이 남아 있어 민감한 화면·실제 사용자 데이터에 사용할 수 없습니다. 현재 APK는 기존 debug 키로 서명한 개발용 빌드입니다.

## 다운로드

[v0.1.9 개발 미리보기](https://github.com/Haran1426/Live-screen-translation/releases/tag/v0.1.9) · Android 11(API30) 이상

| APK | 대상 |
| --- | --- |
| `ScreenLingo-0.1.9-android-arm64-v8a.apk` | ARM64 Android 기기 · 실기기 실행 미검증 |
| `ScreenLingo-0.1.9-android-armeabi-v7a.apk` | 지원되는 32비트 ARM 기기 · 실행 미검증 |
| `ScreenLingo-0.1.9-android-x86_64.apk` | x86_64 에뮬레이터 · 이 버전은 API30에서 실행 검증 |

릴리스의 `SHA256SUMS.txt`와 다운로드한 파일의 SHA-256을 비교할 수 있습니다. [릴리스 노트](docs/releases/v0.1.9.md), [체크섬](docs/releases/SHA256SUMS-v0.1.9.txt)

## 기능

- 영어·한국어·일본어·중국어 원문/번역 언어 선택
- MediaProjection 캡처 → 기기 내 ML Kit OCR/번역 → 보안 오버레이
- 번역 카드 이동·접기·멈춤·재개·종료, 화면 잠금/공유 종료 시 자원 정리
- Wi-Fi 전용 모델 준비와 연결 상태 안내, 준비한 모델의 오프라인 번역
- 본문 글자 3단계/카드 높이 2단계, 1/2/4초 화면 확인 간격
- 직접 작성한 다국어·장문·혼합 언어 연습 화면
- 밝은 화이트·옅은 회색/파랑 UI, 필요한 기능·상태 안내 중심의 화면

캡처·번역문을 파일/기록/서버에 저장하지 않습니다. 모델 다운로드 외 번역 서버 API나 유료 서비스를 사용하지 않습니다. Google Translate 공식 출처 배지를 번역 결과 옆에 표시합니다. SDK의 정상 liblog 경로를 보호하는 공개 NDK 콜백이 있어 최소 지원은 Android 11입니다. [로그 보호와 한계](docs/SECURITY.md)

## 사용

1. 원문/번역 언어를 고릅니다.
2. 다른 앱 위 표시 권한을 허용하고 언어 모델을 준비합니다.
3. `화면 번역 시작`에서 앱 안내와 Android 화면 공유에 동의합니다. 매 세션 새 동의가 필요합니다.
4. `번역 연습 화면 열기`에서 작성된 예문으로 동작을 확인합니다.
5. 카드/이동 버튼 또는 알림으로 접기·멈춤·종료합니다.

카드에 가려진 글은 OCR 대상에서 제외되므로 카드를 접어 확인하세요. 원문 언어는 수동 선택입니다. 작은 글자·보안 화면·혼합 언어의 인식과 번역 문맥에는 한계가 있습니다. 확인 간격은 실제 처리 속도의 보장이 아닙니다.

## 개발

검증한 환경은 Flutter 3.44.6/Dart 3.12.2, JDK21, Android compile/target36, min30입니다. Android SDK/NDK와 Flutter를 준비합니다. 저장소의 기존 iOS 기본 골격은 보존하지만 화면 번역은 Android만 구현·검증했습니다.

```sh
git clone https://github.com/Haran1426/Live-screen-translation.git
cd Live-screen-translation
flutter pub get
flutter analyze
flutter test
flutter run -d <android-device>
flutter build apk --release --split-per-abi
```

ABI별 APK는 `build/app/outputs/flutter-apk/`에 생성됩니다. 일반 release 빌드는 이제 별도 배포 키 설정이 필수이며, 키가 없거나 debug 인증서이면 실패합니다. 게시된 v0.1.9는 기존 개발용 서명입니다. [배포 서명과 정식 릴리스 조건](docs/RELEASE.md)을 따릅니다.

메모리가 제한된 Windows 환경에서는 프로젝트/전역 설정을 바꾸지 않고 해당 프로세스의 Gradle 자원만 제한할 수 있습니다.

```powershell
$previousGradleOpts = $env:GRADLE_OPTS
$env:GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=768m -XX:ReservedCodeCacheSize=256m -Dfile.encoding=UTF-8" -Dorg.gradle.workers.max=2 -Dorg.gradle.daemon=false'
try { flutter build apk --release --split-per-abi }
finally { $env:GRADLE_OPTS = $previousGradleOpts }
```

Android 단위/lint 검사는 `android/gradlew`(Windows: `gradlew.bat`)의 `:app:testDebugUnitTest`, `:app:lintDebug`로 실행합니다. 실제 로그/공유 회귀는 작성한 샘플을 사용하는 [QA 절차](docs/QA.md)를 따릅니다. 기기/토큰/권한 보호를 우회하지 않습니다.

## 문서

- [제품 기획](docs/PRODUCT.md) · [구조](docs/ARCHITECTURE.md)
- [개발·검증 기록](docs/DEVELOPMENT.md) · [백로그](docs/BACKLOG.md)
- [기기 QA](docs/QA.md) · [QA 환경](docs/QA_ENVIRONMENT.md)
- [다국어 검증 범위](docs/MULTILINGUAL_QA.md) · [로그 보호](docs/SECURITY.md)
- [초기 학습·개발 계획](docs/learning-plan.md) · [변경 기록](CHANGELOG.md)
- [배포 준비와 서명](docs/RELEASE.md)
- [Google Translate 자산 출처](assets/branding/README.md)

기존 MIT 라이선스를 유지합니다. Google 출처 배지는 Google의 상표/브랜드 지침에 따르며 앱 자체 로고나 제휴 표시가 아닙니다. 정기 개발의 실행/일시중지 상태는 사용자의 Codex 자동화 설정을 따릅니다.
