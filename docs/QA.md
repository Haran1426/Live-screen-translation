# 수동 QA

검증 환경: Android 16/API 36 Medium Phone 및 별도 Android 11/API 30 x86_64 에뮬레이터, 1080×2400. 사용자 실기기는 아직 검사하지 않았다. 캡처 검증에는 앱에 직접 작성한 영어 연습 문장만 사용한다. [OS별 결과와 재실행](QA_ENVIRONMENT.md).

## 시작과 모델

- 깨끗한 설치 시 표시 권한/모델 미준비 → 시작 비활성.
- 표시 권한 설정을 열고 돌아오기 → 준비 상태 갱신.
- 언어 쌍 변경 → 모델 준비 상태 재확인. 같은 언어 쌍은 선택 불가.
- Wi-Fi 없이 준비 → 안내/실패 후 재시도. Wi-Fi 전용 해제 시 모바일 데이터 동작 확인.
- 모델 다운로드 후 재시작 → 준비 상태 유지.
- 화면 공유 취소 → 세션이 시작되지 않고 다시 시작 가능.
- 알림 권한 허용/거부 양쪽에서 화면 공유 절차로 이어짐.

## 실제 번역

- 영어→한국어로 시작하고 연습 화면 열기 → 한국어 결과가 실제로 나옴.
- Android 홈/브라우저로 이동 → 오버레이 유지, 바깥 영역 터치/스크롤 전달.
- 버튼 탭 → 카드 접힘/펼침. 버튼 드래그 후 실수로 접히지 않음.
- 카드 내용 스크롤, Google Translate 출처 배지 표시.
- 멈춤 → 번역문이 지워지고 새 OCR 결과가 도착해도 표시되지 않음.
- 재개 → 현재 화면을 새로 읽음.
- 카드 종료/알림 종료 → 오버레이와 공유 표시 제거, 서비스 없음, 원문/번역문 메모리 초기화.
- 앱 홈/설정/결과 화면에서는 추가 캡처 처리를 쉬어 결과 재번역을 막음. 연습 화면은 예외.

## 생명주기

- 기기 잠금 → 서비스 종료. 잠금 해제 후 임의 재시작 없음.
- Android 시스템의 화면 공유 종료 → 앱 idle로 돌아감.
- 번역 중 표시 권한 해제 → 크래시 없이 종료.
- 세로↔가로 회전 → VirtualDisplay 추가 생성 없이 크기 변경, 이전 처리 결과 폐기.
- 최근 앱에서 작업 제거 → 서비스 종료.
- 같은 앱 프로세스에서 여러 번 새 동의로 시작/종료.

### 생명주기 자동 회귀

실제 최적화 release APK와 작성한 영어 연습 화면을 사용한다. 기존 앱 APK는 바꾸지 않고 테스트 APK만 빌드할 수 있다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location android
.\gradlew.bat :app:assembleReleaseAndroidTest -PscreenlingoTestBuildType=release --console=plain
Pop-Location
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseScenario revoke-overlay
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseScenario screen-lock
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseScenario remove-task
```

API30에서는 `scripts/api30-qa.ps1`로 장치를 시작하고 위 검사에 `-Serial emulator-5556`을 더한다. 끝나면 `scripts/api30-qa.ps1 -Stop`으로 소유 QA VM만 종료한다. 모델은 기존 debug fixture로 먼저 준비되어 있어야 한다. 기본 `privacy` 시나리오는 기존 번역·멈춤/재개·로그 검사를 유지한다. OS별 실제 실행 여부는 QA_ENVIRONMENT.md를 따른다.

- 각 검사는 새 앱 안내와 실제 Android 공유 동의를 거쳐 한국어 번역 결과가 나온 뒤에 종료 조건을 일으킨다.
- `revoke-overlay`: 이 앱의 AppOps 표시 권한을 `ignore`로 바꿔 실제 `Settings.canDrawOverlays`가 false인지 확인한다. Android 설정 앱의 토글 UI를 눌러 본 검사와는 구분한다. 권한은 검사 후 `allow`로 복원한다.
- 0.1.4의 `revoke-overlay`는 정리 후 연습 화면에서 홈으로 돌아가 실제 권한 해제 사유가 표시되는지, 안내 닫기가 동작하는지도 검사한다. 닫은 상태의 중복 이벤트/새로고침, 앱 재진입의 상태 조회, 새로운 같은 오류의 재표시 및 320dp/큰 글꼴은 Flutter 회귀에서 분리해 확인한다. SDK를 강제로 30초간 멈추는 시험은 아니다.
- `screen-lock`: 잠금이 설정되지 않은 QA 에뮬레이터를 sleep/wake하고 키가드를 해제한다. 실기기 PIN/생체 인증 검사가 아니다.
- `remove-task`: 연습 화면에서 번역이 나온 뒤 화면 읽기를 일시정지하고, 공개 `ActivityManager.AppTask.finishAndRemoveTask()`로 이 앱의 작업만 제거한다. 홈/다른 최근 앱 화면을 OCR 입력으로 읽지 않으며 다른 앱의 작업은 제거하지 않는다. 이 앱의 작업 목록이 비었는지 확인한 뒤 세션 자원 정리와 새 동의를 검사한다. 시스템 최근 앱 UI에서 카드를 손으로 밀어 본 검사/OEM 동작과는 구분한다.
- 서비스·오버레이·이 앱의 FGS 알림이 사라지고 시스템 MediaProjection 진단에 이 앱의 활성 공유가 없는지 검사한다. 결과/로그/시스템 진단 원문은 파일에 저장하지 않는다.
- 권한 복원/깨우기 후 4초 동안 세션이 다시 생기지 않는지 관찰한다. 앱을 다시 열어 시작하면 Android 동의 UI가 새로 나오는지 확인하고 취소한다. 메모리 바이트를 직접 검사하거나 무기한 무재시작을 실측한 결과는 아니다.

테스트만 공개 `ActivityManager.getRunningServices`로 이 앱의 서비스 존재를 확인한다. API26 이후 자기 앱 서비스만 반환하는 호환 범위이며 제품 동작에는 사용하지 않는다. [공식 API](https://developer.android.com/reference/android/app/ActivityManager#getRunningServices(int)). 작업 제거는 [공개 AppTask API](https://developer.android.com/reference/android/app/ActivityManager.AppTask#finishAndRemoveTask())를 쓰며, 서비스의 `stopWithTask=true` 설정은 [Service 문서](https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent))를 따른다. 시스템 공유 중지 UI/최근 앱 UI의 제스처/연속 회전은 별도 후속 검사다.

## 시각/접근성

- 좁은 화면과 큰 글꼴에서 잘림/RenderFlex 예외 확인.
- 어두운 시스템 테마, 디스플레이 확대, TalkBack, 접이식/가로 화면 확인.
- 기본 홈의 예시는 디자인 예시임을 알 수 있어야 함.

## 캡처 제한

오버레이에는 FLAG_SECURE가 있어 `adb screencap`에서는 검은 사각형으로 보인다. 실제 화면은 Android Studio의 Running Devices에서 확인한다. 이 보호를 QA 사진을 위해 해제하지 않는다. `uiautomator dump`는 포커스된 Flutter 창 위주로 출력되어 오버레이 텍스트를 누락할 수 있다.

0.1.0에서 debug와 R8 release 모두 SDK 번역문 로그를 관찰했다. 0.1.1에는 공개 NDK 로그 보호를 추가했다. `scripts/verify-privacy.ps1`로 debug Bitmap 경로와 실제 release 화면 공유 경로를 검사하며 API 30/36 x86_64에서 통과했다. 결과/제약은 [로그 보호](SECURITY.md)를 참고한다. arm64 실기기 검증 전까지 샘플 전용과 외부 배포 보류를 유지한다.

모델 준비 무응답 시 2분 후 spinner와 설정 잠금이 풀리는지 확인한다. 이후 실제 SDK 다운로드가 완료돼도 자동으로 성공 표시하지 않으며 새로고침으로 모델 상태를 다시 조회한다. 다운로드가 취소됐다고 안내하면 안 된다.

## 다국어 기본 회귀

다국어 Bitmap 검사는 별도의 [12개 방향 fixture](MULTILINGUAL_QA.md)로 실행한다. 기본 privacy/생명주기 검사의 영어 연습 화면과 혼동하지 않는다.

### 0.1.3 실제 release 연습 화면

```powershell
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseSource ja
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseSource zh
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseSource ko
```

`ja/zh`는 한국어, `ko`는 영어로 번역한다. 기본 `en`은 영어→한국어다. 테스트는 언어 설정을 저장된 설정 경로로 준비하고 실제 앱을 실행한다. Flutter 원문 제목이 그 언어인지 확인한 다음, 실제 Android 공유 동의를 거쳐 번역한다. native 오버레이 TextView에서 결과 키워드를 확인하므로 Flutter 원문 텍스트를 번역 결과로 오인하지 않는다. 멈춤 시 결과 제거/재개 후 결과와 로그 부재도 검사한다.

기존 원문/번역 언어 키만 메모리에 보관했다가 finally에서 원래 값(키가 없었으면 없음)으로 복원한다. 모델/다른 설정은 삭제하지 않는다. 테스트는 SDK/app 심볼에 직접 연결되지 않는 Java/Android API만 사용하며 앱 R8 규칙을 늘리지 않는다. 다른 release 시나리오는 기존 영어 원문을 사용한다. 저장된 설정으로 기동한 검사이며 언어 선택 팝업의 모든 조작이나 나머지 8개 실제 화면 번역 방향을 검증한 결과는 아니다.

### 실제 release의 원문/번역 언어 지정

제품/SDK를 바꾸지 않고 release fixture만 수정했다면 테스트 APK만 먼저 빌드한다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location android
.\gradlew.bat :app:assembleReleaseAndroidTest -PscreenlingoTestBuildType=release --console=plain
Pop-Location
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseSource en -ReleaseTarget ja
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReuseInstalled -ReleaseSource zh -ReleaseTarget ja
```

`-ReleaseTarget en/ko/ja/zh`는 privacy 및 읽기 시나리오의 번역 대상을 지정한다. 생략/`auto`일 때 `ko` 원문은 영어로, 나머지는 한국어로 번역한다. 같은 언어와 release의 읽기 시나리오 이외의 명시적 대상 언어는 거부한다. `reading-limits`와 기존 생명주기는 en→ko다. 모델은 기존 기기 내 준비 흐름으로 마련되어 있어야 한다.

`-ReuseInstalled`는 release/SkipBuild에서만 허용한다. 소스 시각/ABI 보호 검사에 더해 `pm path`가 가리키는 제품 및 테스트 APK의 SHA-256을 각각 로컬 선택 파일과 비교하고, 일치할 때만 재설치 없이 실행한다. 기존 APK가 다르거나 경로/해시를 읽지 못하면 거부한다. 오래된 테스트 APK를 최신 검사로 오인하거나 설치 직후 ADB 재연결을 매 방향마다 유발하지 않기 위한 도구다.

각 방향은 새 Android 동의/원문 화면/실제 native TextView의 번역·멈춤/재개·언어 설정 복원과 로그 검사를 수행한다. 로그 검사에는 개발용 고정 경고를 넣고 보호된 고정 문구가 실제로 읽히는 양성 대조를 요구한다. 원문 앵커·첫 번역 본문·재개 후 번역 본문 및 알려진 SDK payload 로그가 없어야 한다. 실제 결과/예외 문자열/뷰 트리를 출력하지 않는다. 기본 단어/시간 앵커 검사는 문장 전체의 의미나 자연스러움 평가와 구분한다. 실제 실행한 방향/버전/결과는 [다국어 기록](MULTILINGUAL_QA.md)을 따른다.

### 긴 문단·혼합 언어 · 0.1.5

```powershell
.\scripts\verify-privacy.ps1 -Variant release -ReleaseScenario reading-limits
.\scripts\verify-privacy.ps1 -Variant debug -DebugSuite mixed-reading
```

`reading-limits`는 en→ko의 실제 공유/기본 예문 번역 후 메뉴에서 긴 문단으로 전환한다. 작성된 혼합 행/긴 원문과 native 일부 번역 안내·결과를 확인하고 멈춤/재개 시 안내의 제거/복원, 기본 예문 복귀 후 안내 해제를 검사한다. 로그 양성 대조와 작성된 원문 앵커/번역 본문/SDK payload 부재, 기존 언어 설정 복원도 유지한다. 12문단 초과·정확히 600자·동일한 앞부분의 제한 메타데이터 변경·Unicode 경계는 `FrameTextSelectionTest`의 결정적 단위 회귀다.

`mixed-reading`은 작성된 네 언어 행을 하나의 메모리 Bitmap에 넣고 네 OCR 언어 선택을 각각 확인한다. 선택 언어의 도서관 단어와 ASCII 식별자를 검사하고, 네 단어 앵커의 관찰 여부만 고정된 boolean으로 보고한다. 모든 언어/문장 전체가 읽혔다는 점수나 번역 품질 평가는 아니다. 원문/OCR 결과를 출력·파일 저장하지 않고 SDK Task 완료에 맞춰 Bitmap을 정리한다. 테스트 수 4개를 요구한다.

### 0.1.6 CJK 장문과 회전

```powershell
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReleaseScenario reading-content -ReleaseSource ja
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReuseInstalled -ReleaseScenario reading-content -ReleaseSource zh
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReuseInstalled -ReleaseScenario reading-content -ReleaseSource ko
.\scripts\verify-privacy.ps1 -Variant release -SkipBuild -ReuseInstalled -ReleaseScenario reading-rotation -ReleaseSource ja
```

`reading-content`는 작성된 CJK 장문의 제목/원문과 새 번역 결과를 확인한다. 기본 대상은 ja/zh→ko, ko→en이다. 기본 예문에서 장문으로 바뀔 때와 다시 기본으로 돌아갈 때 이전 결과가 바뀌어야 하며 멈춤 시 제거·재개 후 결과도 검사한다. `limit`은 관찰값으로 기록한다. 작성한 전체 본문이 600자 초과라는 사실만으로 화면에서 인식된 한 문단도 600자 초과라고 가정하지 않는다. 영어의 분량 제한 발생 조건은 기존 `reading-limits`에서 계속 엄격히 확인하며 CJK는 이 옵션으로 실행하지 않는다.

`reading-rotation`은 기본 예문의 실제 번역 후 화면 축을 바꿔 본문 viewport 72dp 이상/폭 200dp 이상, 가로 카드의 폭, 최소 48dp 제어와 출처 배지의 카드 내부 배치를 확인하도록 준비했다. 멈춤/재개 제어와 원래 축 복귀 후 새 번역/서비스 유지, 로그/언어 설정 복원도 검사한다. 회전 중 카드가 가린 원문 전체의 번역을 보증하는 검사가 아니다. 변경 전 자동 회전/사용자 회전 설정을 보관하고 finally에서 키 부재까지 복원·확인하며 실패하면 통과하지 않는다.

읽기 영역은 native TextView의 부모 ScrollView로 한정한다. Flutter 페이지도 접근성에서 ScrollView로 노출될 수 있어 클래스 이름만으로 전체 페이지를 카드로 측정하지 않는다. 테스트 APK 수정 전의 기기 미실행 기록과 후속 API30 결과를 [QA 환경](QA_ENVIRONMENT.md)에서 구분한다. 큰 글꼴/TalkBack/전체 연속 회전·OEM은 별도 QA다.

## 모델 준비 연결 안내 · 0.1.7

```powershell
.\scripts\verify-privacy.ps1 -Serial emulator-5556 -Variant release -SkipBuild -ReleaseScenario network-guidance
```

이 시나리오는 `ScreenLingo_API30` 소유 AVD/emulator-5556만 허용한다. 준비된 en→ko 모델을 보존한 실제 최적화 release에서 설정의 Wi-Fi 안내 → `svc wifi/data disable` → 수동 새로고침 후 오프라인 안내 → 홈의 시작 전 앱 동의 안내/취소 → Wi-Fi/모바일 데이터 원래 설정 복원 → Wi-Fi 안내 복귀를 확인한다. 서비스/공유를 시작하지 않으며 캡처·다운로드·로그 보호의 검사는 기존 `privacy`와 구분한다. 초기 Wi-Fi는 켜짐/검증된 기본 연결이어야 하고 모바일 데이터 키는0/1이어야 한다. 다른 VM/휴대폰에서 실행하거나 모델을 삭제해 이 조건을 만들지 않는다. 네트워크 설정 복원도 통과 조건이다.

Flutter 회귀는 대기 중 연결 변경이 같은 SDK 요청에서 안내만 바꾸는지, 연결 조회 실패/5초 무응답/오래된 응답이 준비 완료로 처리되지 않는지, 준비 종료·2분 제한 후 조회가 멈추는지, 모델 캐시가 오프라인 시작을 허용하는지를 검사한다. 320dp/1.4배 글꼴에서 안내·요금 문구·새로고침을 확인한다. 실제 모델 미설치 상태의 Wi-Fi 대기→다운로드 전송/완료, 모바일 과금량, VPN/인증 포털, SDK 취소/삭제는 이 fixture의 검증 범위가 아니다.

## 사용자 카드 크기 · 0.1.8

```powershell
.\scripts\verify-privacy.ps1 -Serial emulator-5556 -Variant release -SkipBuild -ReleaseScenario reading-settings
```

`reading-settings`는 en→ko/세로 시작 전용이다. 기존 읽기 설정의 값/키 부재를 저장하고 기본16/300으로 시작한 뒤 실제 설정 탭에서 `아주 큰 글자`/`높은 카드`를 스크롤해 고른다. 저장24/420 확인 후 Activity를 다시 열어 실제 공유 동의/작성한 연습 화면으로 진행한다. 본문 TextView의 공개 API30 `EXTRA_DATA_RENDERING_INFO_KEY`/`getTextSizeInPx()`로24sp에 해당하는 실제 px를 확인하며, 정보 미확보는 통과가 아니다. native 카드 높이/본문 viewport/48dp 제어·출처 배치, 회전·멈춤/재개·원래 축 복귀, 로그 양성 대조/원문·번역 payload 부재와 언어/읽기/회전 설정 복원을 검사한다. 제품 또는 SDK private API/reflection은 사용하지 않는다.

기존 캡처 release fixture도16/300을 임시 기준으로 사용하고 모든 경우 읽기 값/키 부재를 복원한다. `reading-settings`만 이 기준에서 UI로24/420을 고른다. 캡처를 하지 않는 `network-guidance`는 읽기 설정을 바꾸지 않는다.

높은 카드는 기존의 아래쪽 영어 제목을 가릴 수 있어 이 시나리오의 영어→한국어 앵커만 작성된 상단 제목의 ‘모험’도 허용한다. 기본 `privacy`/`reading-rotation`의 ‘경치’ 조건은 유지한다. 숨겨진 영역을 인식했다고 주장하지 않는다. 다른 글자/높이 조합·시스템 글꼴·작은 가로 화면은 순수 정책/Flutter 회귀와 실제 기기 QA를 구분하며, 전체 조합의 실제 접근성/TalkBack을 보증하지 않는다.

선택은 접근성에서 관찰한 레이블/스크롤 viewport의 실제 경계로 터치하고 각 값의 저장 확인까지 기다린다. 큰 글자 미리보기의 재배치 직전 좌표나 비활성 선택의 action 수신을 저장 성공으로 세지 않는다. 연습 화면에서는 관찰한 이동 버튼을 실제 드래그해 작성된 상단 제목 위에서 치우고, 새 버튼 경계가 제목과 겹치지 않음을 확인한다. 이는 일반 사용자 조작 경로이며 OCR 제외 영역/보안 플래그를 해제하는 테스트 스위치가 아니다.

## 프레임 대기 회귀 · 0.1.2

`FrameWorkGuardTest`는 가짜 단조 시계와 스케줄러로 다음 경계를 확인한다. 실제 SDK를 강제로 멈추거나 취소한 시험은 아니다.

- 완료 없는 작업은 정확히 30초 경계에서 한 번만 만료하고 새 작업/늦은 결과를 거부한다.
- 일시정지/재개·회전의 결과 무효화는 진행 중 작업을 중복 실행하거나 제한 시간을 연장하지 않는다.
- 오래된 완료/타이머는 새 작업의 소유권을 해제하지 않는다.
- 타이머 실행보다 먼저 도착한 30초 이후 완료/결과도 만료 처리한다.
- 정상 완료/종료는 타이머를 제거하며 종료 이후 콜백은 무시한다.

실제 정상 번역 경로는 debug Bitmap fixture와 최적화 release 화면 공유 fixture로 별도 검사한다. 실기기에서 고의 SDK 무응답, 메인 스레드 정지, 장시간 메모리 동작은 미검증이다. 시간 초과는 SDK 입력 이미지의 안전한 해제를 보장하는 취소 API가 아니다.
