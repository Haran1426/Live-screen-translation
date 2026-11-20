# 텍스트 로그 보호 · 0.1.1

## 문제와 대응

0.1.0에서 ML Kit 17.0.3이 `native` 태그의 정보 로그에 인식/번역 내용을 기록하는 것을 debug와 R8 release 모두에서 관찰했다. 앱 코드의 Log 호출을 제거하거나 Logcat 뷰어에서 필터링하는 것으로는 이미 기록된 데이터를 보호할 수 없다.

0.1.1은 `ModelRepository.translator()`에서 SDK 클라이언트를 만들기 전에 `NativeLogPrivacy.requireProtection()`을 호출한다. 작은 C++ JNI 라이브러리에서 Android의 공개 `__android_log_set_logger` API를 사용해 **이 앱 프로세스**의 liblog 콜백을 설정한다.

- 정보/디버그 로그는 출력하지 않는다.
- 경고/오류는 심각도만 유지하고 태그와 본문을 고정 문구로 대체한다. 원래 태그, 파일명, 본문, 예외 문자열을 복사하지 않는다.
- 로더가 실패하면 SDK Translator 생성을 거부한다. 캡처 ImageReader를 만들기 전이므로 보호 없이 화면 번역으로 진행하지 않는다.
- 시스템 속성 변경, 루트, 로그 버퍼 삭제, SDK private API/reflection은 사용하지 않는다.
- 낮은 우선순위 필터만 사용하는 방식은 피했다. Android 문서상 태그별 시스템 속성이 최소 로그 레벨보다 낮은 우선순위를 허용할 수 있기 때문이다.

[Android NDK 공식 Logging API](https://developer.android.com/ndk/reference/group/logging)는 사용자 로거를 API 30부터 공개한다. 따라서 최소 지원 버전을 Android 8에서 **Android 11(API 30)**로 조정했다. 구형 Android를 다시 지원하려면 동일한 보호가 가능한 엔진/설계를 검증한 후 지원 범위를 넓혀야 한다.

이 콜백은 모델 준비 후 Flutter/프레임워크 진단에도 적용된다. 경고 발생 신호는 남지만 원래 상세 로그는 볼 수 없다. 원인 분석은 작성한 테스트 문장과 디버거/상태 검사로 진행하고, 사용자 화면 로그를 남기는 우회 스위치를 추가하지 않는다.

## 회귀 검사

`scripts/verify-privacy.ps1`는 에뮬레이터에서만 실행한다. 실제 사용자 데이터, 임의의 다른 앱, 저장된 캡처 이미지를 입력하지 않는다. 테스트는 로그 버퍼를 메모리에서 검사하고 성공/실패 상태만 반환한다.

동일 APK를 다른 OS에서 검사할 때는 `-Serial emulator-5556 -SkipBuild`를 사용할 수 있다. 소스보다 오래된 APK와 로그 보호 네이티브 라이브러리가 없는 APK는 거부한다. SDK/NDK/의존성을 변경한 뒤에는 기본 명령으로 빌드하고 검사한다. [API30 재실행 환경](QA_ENVIRONMENT.md).

```powershell
.\scripts\verify-privacy.ps1
```

debug 검사는 직접 작성한 문장을 메모리 Bitmap에 그려 OCR/한국어 번역이 실제로 동작하는지 확인한다. Java Log의 원문/동적 태그/예외 및 ML Kit native 진단 모두에서 테스트 텍스트가 Logcat으로 나가지 않는지 검사한다. 모델이 없으면 Wi-Fi 조건으로 모델만 준비한다.

release 검사는 SDK 코드를 직접 호출하지 않는 별도의 Java Instrumentation이다. 프레임워크 API만 사용하므로 테스트가 앱 R8 보존 범위를 늘리지 않는다. 실제 앱 실행 → 안내 → Android 화면 공유 동의 → 앱의 영어 연습 화면 → 한국어 오버레이 → 해당 프로세스 Logcat 검사 → 서비스 종료를 수행한다. release가 실제로 debuggable=false인지도 확인한다. 표시 권한은 테스트 패키지에만 준비하며 화면 공유 동의 토큰은 저장/재사용하지 않는다.

debug와 release 테스트를 분리한 이유는 release 테스트가 대상 APK의 최적화로 제거된 SDK/AndroidX/Kotlin 메서드를 직접 링크하는 문제를 피하기 위해서다. 앱 최적화를 끄거나 SDK 전체에 보존 규칙을 추가하지 않는다.

## 검증의 한계

2026-11-19 실행 결과: 최신 0.1.1에서 debug fixture 1개 및 실제 R8 release UI fixture 1개 통과. Flutter 11개/Android 단위 5개 통과, Android lint 오류 0개(경고 16개). release 검사는 실제 공유 동의 후 한국어 오버레이 결과가 나오면서 원문/번역문과 SDK payload 진단이 프로세스 로그에 없는 것을 확인했다.

추가 실행에서 API 30(Android 11) x86_64 에뮬레이터의 debug 및 실제 R8 release 검사도 각각 통과했다. 지원 하한의 공개 NDK 콜백, 모델 준비, OCR/번역, 매 세션 화면 공유와 오버레이가 동작하고 테스트 텍스트 로그가 없는 것을 확인했다. ARM64 실기기 실행과 다른 OEM 환경은 추가 검증이 필요하다. SDK/NDK/새 네이티브 의존성을 바꾸면 양쪽 회귀 검사를 다시 실행한다.

0.1.2의 프레임 기한/응답 무효화 변경 후에도 API30/36에서 두 fixture가 통과했다. 실제 release 검사는 멈춤 시 번역문 삭제와 재개 후 새 번역도 확인한다. 프레임 무응답/늦은 콜백은 별도 가짜 시계/스케줄러 단위 회귀로 확인했으며 SDK 내부 작업을 강제로 중단하거나 입력 메모리를 즉시 지우는 보안 보장은 아니다. [작업 경계와 한계](ARCHITECTURE.md), [검증 내역](DEVELOPMENT.md).

이는 정상 liblog 경로의 SDK 텍스트 로그에 대한 조치다. OS 크래시 덤프, 루팅된 기기, 향후 SDK의 stderr/직접 logd 호출 또는 로거 교체까지 모두 검증한 보안 감사를 의미하지 않는다. 검증 범위를 넘는 기기나 민감한 실제 화면으로 확대하지 않고 작성한 샘플 위주로 개발을 이어간다. 외부 배포는 여전히 별도 단계다.

추가로 API36 debug에서 4개 원문의 Bitmap OCR/12개 번역 방향과 로그 검사가 모두 통과했다. 고정된 개발용 경고의 안전한 대체 문구도 읽어 빈 로그를 성공으로 오인하지 않게 했다. 이는 실제 release CJK 캡처나 모든 언어 표현의 보안/품질 보장을 추가한 것은 아니다. [다국어 실행 범위](MULTILINGUAL_QA.md).
