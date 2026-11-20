# 구조

## 구성

0.1.9 UI는 사용자 요청에 따라 밝은 테마를 명시한다. Flutter의 화이트/옅은 중립색 표면·파랑 강조, 밝은 AppBar/시스템 바, 짧은 기능 제목을 사용하고 홈의 모의 번역 미리보기/홍보 문구/장식 배지는 제거했다. 각 탭은 별도 PageStorageKey로 스크롤 위치를 관리한다. 네이티브 보안 카드는 흰 배경/진한 본문/파랑 제어를 사용하며 Google의 수정하지 않은 color-regular 배지를 기존 Flutter 원본에서 대응 밀도로 복사한다. 캡처 제외/보안 플래그/크기 정책/동의와 처리 엔진은 유지한다.

`lib/src/app.dart`는 홈/언어/준비 상태/안내/설정과 4개 원문 언어의 연습 화면을 제공한다. `SessionController`는 UI 상태, 권한/모델 준비 조건, 중복 조작 방지를 담당한다. `TranslationBridge`로 네이티브 통신을 격리해 테스트에서 가짜 브리지를 주입한다.

`PracticeSample`은 직접 작성한 원문만 제공한다. 안내 탭과 진행 중 세션의 두 진입 경로가 선택한 원문 언어 및 같은 controller bridge를 `PracticeScreen`에 전달한다. 화면이 열리고 닫힐 때의 practice 가시성, 복사할 예문, 제목/본문이 이 설정을 따른다. 번역문 예시를 실제 결과처럼 렌더하지 않으며 화면을 여는 동작 자체는 캡처를 시작하지 않는다.

`MainActivity`는 `screenlingo/control` MethodChannel과 `screenlingo/session` EventChannel을 연결한다. 모델 상태 조회/다운로드와 화면 동의를 처리하고, 동의 토큰을 저장하지 않고 곧바로 서비스로 전달한다.

`ScreenTranslationService`는 알림을 게시한 뒤 MediaProjection을 얻어 VirtualDisplay/ImageReader를 만든다. 주기마다 단일 작업만 수행한다. 픽셀 복사는 HandlerThread, OCR/번역은 ML Kit 비동기 Task에서 처리한다. 화면 이미지는 OCR 작업이 끝나면 recycle한다.

`TranslationOverlay`는 원문 위치 표시, 이동 가능한 버튼, 접이식 번역 카드에 TYPE_APPLICATION_OVERLAY 창을 각각 사용한다. 0.1.11부터 매 세션 카드를 접은 채 시작한다. 이전 표시 방식 설정은 읽지 않는다. 창에 FLAG_SECURE를 설정하고 OCR 바운딩 박스를 버튼·카드의 화면 좌표와 비교해 자기 번역문이 다시 입력되는 것을 막는다. 카드 뒤 영역은 접어서 확인한다.

원문 위치 표시 창은 터치를 받지 않는다. OCR 문단을 캡처 가로·세로 비율로 각각 화면 좌표에 매핑하고, 캐시된 번역에도 새 좌표를 사용한다. 확인 주기마다 창을 숨기고 대기열을 비운 뒤 새 프레임을 읽는다. 공유가 중단되거나 일시정지·회전·앱 표시 전환이 생기면 표시를 지우거나 진행 중 결과를 무효화한다. 배지·시스템 바·이동 버튼·이웃 문단과 겹치거나 번역문이 12sp 이상으로 들어가지 않으면 원문을 남긴다. 배치에 실패한 경우에도 카드에서 읽을 수 있다. Android 12 이상에서는 [공개 입력 투과 한도](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE) 이내의 창 불투명도를 사용한다. 실제 앱의 입력 수용과 OEM 캡처 동작은 기기 QA에서 확인한다.

0.1.6의 `OverlayCardLayout`은 dp 단위의 화면 폭/높이/글꼴 배율로 창 크기를 계산하는 순수 정책이다. 세로는 기본 300dp/화면 절반 상한, 가로는 300~420dp의 오른쪽 폭과 세로 여백 내 300dp 목표 높이를 사용한다. 글꼴 배율에 따라 높이를 최대 1.5배 목표로 늘리되 화면 범위를 넘지 않는다. 가로에서는 멈춤/종료 View를 헤더로 옮기고 비어 있는 하단 제어 행을 숨기며 상태/본문 스크롤/출처는 유지한다. 가로 제목을 짧게 하고 세로로 돌아오면 원래 제어 행/제목을 복원한다. 실제 창 위치로 제외 영역을 계속 계산하며 보안 플래그는 유지한다. 정책의 단위 테스트는 실제 Android 측정/접근성/모든 큰 글꼴을 보증하지 않는다.

`SessionStore`는 메인 스레드에서만 사용하는 메모리 상태다. 이벤트에는 phase/message/blocks/elapsedMs만 포함한다. 앱을 다시 열면 현재 세션을 조회한다. 서비스 종료 시 원문/번역문을 비운다. 설정만 SharedPreferences에 저장한다.

0.1.8은 `cardTextSp`(16/20/24), `cardHeightDp`(300/420)를 기존 설정 map/SharedPreferences/새 공유 세션 Intent에 전달한다. 오래된 설정은16/300을 사용한다. 네이티브 채널/서비스에서 허용 범위를 검증하고 저장된 읽기 값이 허용 범위 밖이면 해당 값만 기본값으로 읽는다. 활성 세션은 시작 시 받은 불변 설정을 사용하며 Flutter가 변경을 잠근다. 본문 TextView의 sp만 변경하고 헤더/버튼/배지는 유지한다. 레이아웃 목표 높이는 `(선택 높이 + (본문sp−16)×6) × 시스템배율(1~1.5)`dp다. 세로 기본 높이는 화면 절반, 높은 카드는2/3, 가로는 기존 상하48dp 여백 안으로 제한하며 가로 폭 정책은 유지한다. 실제 창의 제외 영역/보안 플래그를 그대로 사용한다. 이는 글자 수/처리 속도/모든 큰 글꼴의 읽기 품질을 보증하는 수치가 아니다.

앱 홈/설정/결과가 포그라운드이면 캡처 처리를 쉬며 연습 화면만 예외다. 상·하단 시스템 UI도 OCR에서 제외한다. 보이는 마지막 결과가 자신의 입력으로 다시 들어오는 문제를 방지한다.

릴리스는 R8 최적화를 유지한다. ML Kit의 오래된 firebase-components 의존성이 기본 생성자 보존 규칙을 누락해 초기화가 실패하므로 `proguard-rules.pro`에서 ComponentRegistrar의 기본 생성자만 보존한다. SDK 전체 최적화를 끄지 않는다. [Firebase upstream 수정](https://github.com/firebase/firebase-android-sdk/pull/6044)을 참고했으며 실제 release APK 설치로 검증한다.

일반 release 빌드는 `preReleaseBuild`의 서명 검사를 필수로 거친다. 비공개 키 설정의 완전성/키 접근/인증서 유효기간을 검사하고 Android Debug 인증서는 거부한다. 자동 debug fallback은 없다. 작성된 샘플의 에뮬레이터 QA에만 `screenlingoQaDebugSigning=true`를 명시해 기존 debug 인증서와 최적화 설정을 사용한다. `verify-privacy.ps1`은 각 빌드 명령 인자로만 이 값을 전달하고 환경/프로젝트/전역 설정에 저장하지 않는다. 서명 검사는 ARM64·실제 기기 동작 검사와 별개다. [배포 조건](RELEASE.md).

## 상태와 비동기 경계

idle/error → Android 동의 → starting → running ↔ paused → idle/error.

`SessionController`는 EventChannel과 현재 상태 조회 모두 같은 경로로 적용한다. native `error` 메시지와 사용자 조작 오류를 구분해 홈의 안내 카드로 전달한다. 같은 error phase/메시지의 반복 전달은 닫은 안내를 복원하지 않는다. 다른 메시지 또는 새 세션 이후의 실패는 새 안내이며, 정상 세션 전이는 이전 native 안내를 비운다. 동의 취소 안내는 이전 실패 상태의 재조회에 덮이지 않는다. 안내의 닫힘 상태는 현재 Flutter controller 메모리에만 남는다.

Flutter에서 시작/설정/다운로드 중 버튼을 잠근다. 서비스의 `FrameWorkGuard`는 프레임별 ticket과 epoch로 중지·회전·일시정지 이전 OCR/번역 응답을 무효화한다. 처리 중 추가 프레임은 대기한다. 무효화만으로 진행 중 SDK 작업의 소유권을 풀지 않으며, 해당 작업 완료 후에만 다음 프레임을 허용한다. 중복된 오래된 완료 콜백은 새 작업을 해제하지 않는다. 동일 원문 목록이면 재번역하지 않고, 세션 LRU 캐시는 최대 160개 문단이다.

`FrameTextSelection`은 시스템바/오버레이 제외 이후의 OCR 문단을 다듬어 최초 12개, 각각 600 Unicode 코드포인트까지 선택한다. OCR의 문단 내부 줄바꿈을 이어 쓰고 일본어·중국어 문자 사이에는 추가 공백을 넣지 않는다. 긴 문단은 420~600자 사이에 있는 문장 경계, 그다음 단어 경계를 우선한다. 적절한 경계가 없으면 기존 Unicode 코드포인트 기준으로 자른다. 잘린 문단은 카드에만 보인다. 원문 후보 인덱스를 유지해 빈 조각 뒤 문단에 다른 번역이 붙는 일을 막는다. 제외/축약 개수와 좌표는 메모리에만 유지하며 번역할 앞부분이 같으면 SDK를 재호출하지 않는다.

`LongPracticeSample`은 각 원문 언어의 600자 초과 문단과 네 문자 체계의 작성된 혼합 행이다. 같은 PracticeScreen 내부 메뉴에서 전환하며 복사/bridge 가시성은 유지한다. 기본 예문의 배치는 유지한다. 실제 결과를 모의로 만들거나 자동 언어 감지를 추가하지 않는다.

한 프레임은 픽셀 복사 시작부터 OCR 및 모든 문단 번역 완료까지 30초 제한을 공유한다. `elapsedRealtime`과 메인 Handler 타이머로 검사하고, 앱이 보이거나 일시정지돼도 진행 중 작업의 제한은 유지한다. 콜백 자체도 만료를 확인해 타이머보다 먼저 큐에 도착한 늦은 결과를 차단한다. 초과하면 자동 재시도 대신 세션을 종료하며 새 시작에는 새 Android 동의가 필요하다. 늦은 결과는 UI/캐시에 넣지 않는다. 이 정책은 SDK Task의 강제 취소가 아니다. OCR 입력 Bitmap은 시간 초과 시 조기 recycle하지 않고 OCR Task 완료 콜백에서 정리한다. SDK가 영원히 끝나지 않으면 그 입력이 SDK에 남을 수 있으며, 메인 스레드 자체가 멈춘 경우 타이머 실행도 늦어질 수 있다.

Task 콜백의 메인 스레드 실행과 완료 경계를 기준으로 자원을 정리한다. [Task 공식 API](https://developers.google.com/android/reference/com/google/android/gms/tasks/Task), [ML Kit OCR 입력/비동기 처리](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)를 참고했다. SDK private API나 임의의 취소 구현은 사용하지 않는다.

모델 준비 Future는 2분 후 UI 대기를 끝낸다. 이 제한은 SDK 다운로드 취소가 아니다. 늦게 완료된 응답으로 UI가 임의로 성공 상태가 되지 않으며, 새로고침/앱 복귀 시 실제 모델 상태를 조회한다.

0.1.7의 `getDownloadNetwork`는 기존 ACCESS_NETWORK_STATE 권한으로 ConnectivityManager의 기본 연결과 capabilities만 읽고 wifi/other/offline/unverified/unknown 코드 하나를 반환한다. INTERNET/VALIDATED를 모두 확인하며 VPN은 underlying Wi-Fi를 단정하지 않고 unknown으로 안내한다. SSID/IP/네트워크 이름이나 예외를 전달·저장·출력하지 않고 추가 서버 요청도 없다. 앱 복귀/수동 새로고침/준비 시작 시 조회하고, 준비 대기 중에만 5초 간격으로 조회한다. 한 조회는 5초 제한/중복 방지를 적용하고 준비 종료·오류·2분 제한·dispose에서 타이머를 정리한다. 늦은 조회는 revision으로 무시한다. 이는 간헐적 안내용 snapshot이며 실시간 연결 감지나 실제 SDK 전송 상태가 아니다. 모델 준비 성공은 계속 SDK Task와 모델 조회로만 판정하고 `requireWifi` 조건/캐시/시작 조건은 유지한다.

`NativeLogPrivacy`는 모든 Translator 생성 전에 공개 NDK `__android_log_set_logger` 콜백을 설치한다. 정보 로그는 버리고 경고/오류는 원문·태그·파일명·예외 내용을 복사하지 않는 고정 문구로 바꾼다. 앱 프로세스에만 적용하며 OS 로그 설정이나 ML Kit 내부 API는 변경하지 않는다. JNI 라이브러리가 로드되지 않으면 번역기 생성을 거부한다. [설계/검증 범위](SECURITY.md).

같은 언어쌍의 읽기/간격/네트워크 설정 저장은 기존 모델 준비 상태를 유지하며 SDK 모델 조회를 추가로 기다리지 않는다. 언어쌍이 바뀌면 준비 상태를 비우고 해당 모델을 다시 조회한다. 시작 전 네이티브 모델 확인과 활성 세션 설정 잠금은 계속 수행한다.

## Android 제약

- minSdk 30(Android 11). 공개 NDK 로그 콜백이 가능한 버전만 지원한다. MediaProjection foreground service와 표시 권한을 사용한다. 앱은 AccessibilityService 권한을 사용하지 않는다. 테스트 APK만 Instrumentation의 UiAutomation을 사용한다.
- Android 14+에서는 전체 디스플레이 공유 구성을 요청해 오버레이의 화면 좌표와 캡처 좌표를 맞춘다.
- 매 시작마다 새 사용자 동의. VirtualDisplay는 세션당 한 번만 생성하고 회전은 resize/setSurface로 처리한다.
- 프로세스가 종료돼도 복구하지 않는다(START_NOT_STICKY). 화면 잠금, 공유 중지, 앱 작업 제거, 권한 해제 시 종료한다.
- 알림 권한은 선택이며 거부해도 카드에서 중지할 수 있다. Android 시스템의 공유 제어도 유지된다.

## 참고한 공식 문서

- [MediaProjection](https://developer.android.com/media/grow/media-projection)
- [MediaProjection foreground service](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Text recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [온디바이스 번역](https://developers.google.com/ml-kit/language/translation/android)
- [Android 기본 연결과 네트워크 상태](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- [공개 접근성 렌더링 정보(API30)](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.ExtraRenderingInfo)
- [번역 사용 지침](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Google Translate 출처 표시](https://cloud.google.com/translate/attribution)

문서 확인일: 2026-11-19 (한국 시간).
