# 설치 배포 준비

현재 v0.1.9 APK는 다운로드·설치할 수 있지만 개발용 서명과 샘플 전용 제한이 있다. 파일을 다운로드할 수 있다는 사실과 실제 사용자 화면에서 사용 가능한 정식판은 구분한다. GitHub의 pre-release 표시만 없애서 검증을 대신하지 않는다.

## 정식 릴리스 통과 조건

- [ ] 실제 ARM64 Android 11 이상 기기에서 debug의 작성된 Bitmap OCR/번역과 최적화 release의 실제 공유·연습 화면·번역·로그 보호를 확인한다. 에뮬레이터/설치 성공/ARM APK 빌드는 대체 증거가 아니다.
- [ ] 해당 배포 후보의 지원 하한/최근 OS, 네 언어 기본 번역, 읽기 설정/큰 글꼴/가로 화면, 동의 취소/새 동의, 멈춤/종료/잠금/표시 권한 철회/시스템 공유 중지의 결과를 기록한다. 실제 기기 조작은 연습 문장에 한정하고 사용자 다른 앱을 자동 검사하지 않는다.
- [ ] API33/34 알림·MediaProjection 동작과 OEM 권한·종료 동작을 검사한다. 실제 실행 범위는 QA_ENVIRONMENT/MULTILINGUAL_QA에 기록한다.
- [ ] 배포 전용 키를 안전하게 보관하고 백업한다. 배포용 앱과 향후 업데이트는 같은 인증서를 사용한다. 키/비밀번호를 저장소, GitHub 첨부, 채팅에 올리지 않는다.
- [ ] 최종 서명 APK의 versionCode/versionName/minSdk/ABI/비디버그 상태/서명과 SHA-256을 확인하고 해당 파일로 설치·동작을 검사한다.
- [ ] 개인정보처리방침·SDK 고지·공식 출처·실제 측정 범위에 맞는 사용 제한을 확인한다. 검증하지 않은 성능·정확도·배터리 수치는 주장하지 않는다.

미충족 항목이 있으면 정식판 게시를 보류한다. 스토어 등록은 GitHub 설치 배포와 별도다.

## 배포용 서명

일반 `flutter build apk --release --split-per-abi`는 배포용 키가 없거나 debug 인증서이면 실패한다. 자동 debug 서명 fallback은 없다. `android/key.properties.example`의 형식으로 비공개 속성 파일을 준비한다. 기본 경로는 Git에서 제외한 `android/key.properties`이고, 저장소 밖 파일은 `SCREENLINGO_KEY_PROPERTIES` 환경 변수로 지정한다. 상대 `storeFile`은 속성 파일이 있는 폴더를 기준으로 해석한다.

```powershell
$env:SCREENLINGO_KEY_PROPERTIES = 'D:/private/ScreenLingo/key.properties'
flutter build apk --release --split-per-abi
```

`verifyReleaseSigning`은 키 파일/필수 값/별칭/개인키 비밀번호/인증서 유효기간과 Android Debug 인증서 여부를 검사한다. 검사 실패에 비밀 값이나 원래 예외를 출력하지 않는다. 이 검사는 기기 QA를 대신하지 않는다. 환경 변수에 QA 예외가 남아 있으면 배포용 빌드를 진행하지 않는다.

기존 debug 서명 v0.1.9에서 다른 인증서의 배포용 APK로는 덮어쓰기 업데이트가 되지 않는다. 배포 키가 확정되면 최초 전환 시 기존 앱 제거가 필요할 수 있음을 안내한다. 사용자의 설치 앱/다운로드 모델을 임의 삭제하지 않는다.

## 에뮬레이터용 최적화 QA 빌드

기존 모델을 보존하는 QA에서만 `screenlingoQaDebugSigning=true`로 명시해 debug 인증서의 최적화 APK를 만든다. `scripts/verify-privacy.ps1`의 빌드 경로는 각 명령의 인자로만 이 값을 전달한다. 환경/프로젝트/전역 설정에 저장하지 않는다. `-SkipBuild`의 소스/보호 라이브러리 검사는 그대로 유지한다.

테스트 APK만 다시 빌드하는 경우:

```powershell
Push-Location android
.\gradlew.bat :app:assembleReleaseAndroidTest -PscreenlingoTestBuildType=release -PscreenlingoQaDebugSigning=true --console=plain
Pop-Location
```

직접 QA용 앱을 빌드해야 할 때:

```powershell
flutter build apk --release --split-per-abi --android-project-arg=screenlingoQaDebugSigning=true
```

이 출력은 서명/기기 검증을 통과한 정식 APK로 게시하지 않는다. Windows에서 Flutter를 통한 Gradle 환경 변수의 전달에 의존하지 않고 명령 인자를 사용한다.

## 근거

- [Android 앱 서명](https://developer.android.com/studio/publish/app-signing)
- [Android 출시 준비](https://developer.android.com/studio/publish/preparing)
- [프로젝트 로그 보호](SECURITY.md) · [실행 QA](QA.md) · [남은 검증](BACKLOG.md)
