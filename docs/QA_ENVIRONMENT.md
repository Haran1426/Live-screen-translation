# Android QA 환경

## 장치와 결과

| 환경 | 이미지/ABI | 확인한 경로 | 결과 |
| --- | --- | --- | --- |
| 기존 Medium Phone, API 36 | x86_64 | debug Bitmap OCR/번역/로그, 최적화 release 실제 화면 공유/오버레이/로그 | 0.1.1 통과, 이전 실행 |
| 기존 Medium Phone, API 36 | x86_64 | 위 경로 + release 멈춤 시 결과 삭제/재개 후 새 번역 | 2026-11-19 0.1.2 통과 |
| 기존 Medium Phone, API 36 | x86_64 | release 표시 권한 해제/화면 잠금 → 세션 자원 정리·4초 무재시작·다음 시작의 새 동의 | 2026-11-19 0.1.2 통과 |
| 기존 Medium Phone, API 36 | x86_64 | release 번역 후 읽기 일시정지 → 공개 AppTask API 작업 제거·자원 정리·4초 무재시작·새 동의 | 2026-11-19 0.1.2 통과 |
| 기존 Medium Phone, API 36 | x86_64 | debug 메모리 Bitmap 4개 문자 체계/12개 번역 방향 기본 키워드·로그/안전 로그 대조 | 2026-11-19 0.1.2, 12개 통과 |
| 기존 Medium Phone, API 36 | x86_64 | 실제 R8 release en→ko/ja→ko/zh→ko/ko→en 연습 화면/공유/오버레이/멈춤·재개/로그 | 2026-11-19 0.1.3, 각 1개 통과 |
| 기존 Medium Phone, API 36 | x86_64 | 실제 R8 release 권한 해제 → 홈 종료 사유 표시·닫기·정리·4초 무재시작·새 동의 | 2026-11-19 0.1.4, 1개 통과 |
| 기존 Medium Phone, API 36 | x86_64 | 같은 0.1.4 제품 APK의 실제 release 12개 번역 방향·멈춤/재개·로그 양성 대조·설정 복원 | 2026-11-19, 개별 방향마다 1개 통과, fixture 보강 전후 합산 |
| 기존 Medium Phone, API 36 | x86_64 | 0.1.5 실제 release 영어 장문의 일부 번역 안내·멈춤/재개·기본 복귀·로그/설정 복원 | 2026-11-19, 1개 통과 |
| 기존 Medium Phone, API 36 | x86_64 | 0.1.5 debug 일반 OCR/번역/로그 1개 및 혼합 Bitmap 4개 선택·단어 관찰·로그 양성 대조 | 2026-11-19, 각각 통과 |
| 별도 ScreenLingo_API30, API 30 | Google APIs Android 11 revision 16, x86_64 | 동일한 debug 경로와 실제 release 화면 공유/오버레이/로그 | 2026-11-19 0.1.1 통과 |
| 별도 ScreenLingo_API30, API 30 | Google APIs Android 11 revision 16, x86_64 | 위 경로 + release 멈춤 시 결과 삭제/재개 후 새 번역 | 2026-11-19 0.1.2 통과 |
| 별도 ScreenLingo_API30, API 30 | Google APIs Android 11 revision 16, x86_64 | 동일 0.1.4 release의 표시 권한 해제(홈 사유·닫기 포함)/잠금/AppTask 작업 제거 → 정리·4초 무재시작·새 동의 | 2026-11-19, 각 1개 통과 |
| ARM64 실기기 | 없음 | 아직 실행하지 않음 | 미검증, 샘플 전용 제한 유지 |
| 0.1.6 후속 실행, 연결 Android 장치 없음 | — | 로컬 native 정책/분석/lint/빌드만 수행 | native 25개/분석/lint/앱·테스트 APK 빌드 통과, 기기 회귀 미실행 |
| 별도 ScreenLingo_API30, API 30 후속 실행 | x86_64 | 같은 0.1.6 debug의 Bitmap OCR/12방향 번역 및 혼합4선택/로그 양성 대조 | 2026-11-19, 개별 실행 `OK (12 tests)`/`OK (4 tests)` |
| 별도 ScreenLingo_API30, API 30 후속 실행 | x86_64 | 실제 R8 release CJK 장문3개/기본4언어의 회전/영어 분량 제한·새 동의/제어·로그/설정 복원 | 2026-11-19, 개별1개씩 총8개 통과 |
| 별도 ScreenLingo_API30, API 30 | x86_64 | 0.1.7 실제 R8 release Wi-Fi/오프라인 안내·수동 새로고침·캐시 시작 전 안내/취소·기존 네트워크 설정 복원 | 2026-11-19, `network-guidance` 1개 통과. 실제 미설치 모델 다운로드 검사는 별도 |
| 별도 ScreenLingo_API30, API 30 | x86_64 | 새0.1.7 debug Bitmap OCR/번역/로그 보호와 같은 release 실제 공유·영어 번역·멈춤/재개·로그 양성 대조 | 2026-11-19, 각 `OK (1 test)`. 소유 VM만 종료/설정·모델 보존 |
| 별도 ScreenLingo_API30, API 30 | x86_64 | 0.1.8 최종 debug Bitmap OCR/번역/로그, 실제 release24sp/420 선택·재실행·본문 px/카드·제어 실측/회전·로그/설정 복원, 기본 release 번역·멈춤/재개·로그 | 2026-11-20 KST, 각각 `OK (1 test)` 총3개. 기존 모델/설정 보존, 소유 VM만 종료 |
| 별도 ScreenLingo_API30, API 30 | x86_64 | 0.1.9 밝은 UI의 실제 R8 release 읽기 선택/큰 카드·제어·출처/회전/공유·번역·멈춤·재개/로그·설정 복원과 기본 privacy | 2026-11-20 KST, 각각 `OK (1 test)` 총2개. 모델/설정 보존 후 소유 VM만 종료 |

API 30 테스트의 release는 `debuggable=false`, versionName 0.1.1 및 0.1.2의 x86_64 split APK다. Android 11의 실제 MediaProjection 동의를 거쳐 앱의 영어 연습 화면에서 한국어 오버레이가 나오는지 검사했다. 원문/번역문 및 알려진 SDK payload 진단이 해당 프로세스 Logcat에 없는 것도 확인했다. 테스트 종료 후 서비스가 없음을 확인했다. 단순 설치 성공을 번역 성공으로 간주하지 않는다.

0.1.1에서 테스트한 APK SHA-256:

- debug universal: `EDA61AA70528B354A24A022BE725818CDEA85D6BBC6AD312D3302B816C223361`
- release x86_64 split: `D8F90417C771765C58FEED7CD165DD0C8E0693BD810B0CEFE02CE828712D90AD`

0.1.2 초기 검증 APK SHA-256(설정의 표시 버전 수정 전, API30/36):

- debug universal (fixture versionCode 4002): `B6FE66D33BEFBE2AA66F35575FB67F11FD221F5C0E2BED4C4D4BC65BC05D1935`
- release x86_64 split (versionCode 4003): `41C9B734BCE4453539EF45AA8F222045BE44322886A855FD012D4B584BEDCA1E`

0.1.2 최종 APK SHA-256(설정 표시 버전도 0.1.2로 수정):

- debug universal (fixture versionCode 4003): `ADF2F63B517E59CD77FB1786053867A09F00950AE2A90F34C76A401BB7CC1BE1`
- release x86_64 split (versionCode 4003): `AFB4B64A289023072200A308F2EE2EEAE88010A1B82C695F4F9965ADC8A17C0E`

0.1.3 최종 출력 APK SHA-256:

- debug universal (fixture versionCode 4004): `483D7A8E4A52E8143F2C6CAD93CFD5C00A1B89879A2432DFBC013BE2F4206947`
- release x86_64 split (versionCode 4004): `AAA387A2FB221DE8E8352095B08DD77FDA310386267A05248CB4952E74D11DCD`

실제 release 4개 언어 프로필 검사는 위 최종 release APK에서 수행했다. 최종 CJK 배치를 반영한 debug APK도 빌드했으며 debug SDK 회귀는 이 실행의 초기 0.1.3 빌드에서 통과했다. [연습 화면 QA 이미지](MULTILINGUAL_QA.md).

원문은 개발자가 작성한 예문과 앱의 연습 화면뿐이다. API 30 이미지가 ARM native bridge를 포함해도 이번 검사의 주 ABI는 x86_64다. ARM64 실기기 검증으로 대체하지 않는다.

0.1.4 출력 APK SHA-256:

- debug universal (fixture versionCode 4005): `D9E3280CCE02E52E14F097E65FAC0EE5A2588F030C6B134807CA656CC8E99242`
- release x86_64 split (versionCode 4005): `D41B4D5C37FDB0CB8567D8C7B00A133C450D8BF465EA1A9826FF30A4F771AF02`

SDK/NDK/서비스/로그 보호는 변경하지 않았다. Flutter 오류 안내 및 release fixture를 바꿨다. API36 최종 fixture는 Activity 교체 완료를 동기적으로 기다린 뒤 재동의 버튼을 누른다. 설치 직후 시작 상태만 반환된 불완전 실행과 기존 Activity를 누르던 `fresh-consent` 실패는 통과에 포함하지 않았고, fixture 수정 후 재설치 없는 직접 Runner에서 전체 1개가 통과했다. debug는 빌드 성공이며 이 버전의 debug SDK fixture를 새로 실행한 결과는 아니다.

API30에서는 위 동일 release/최종 test APK로 `revoke-overlay`/`screen-lock`/`remove-task`를 각각 `-SkipBuild`로 실행해 모두 `OK (1 test)`를 확인했다. 매번 실제 공유 동의/영어 연습 화면의 한국어 오버레이/종료 조건/새 동의/로그를 검사했다. 이 실행의 CJK 연습 화면/12방향 Bitmap/실제 SDK 강제 무응답 검사를 의미하지 않는다. 시작 전 여유 RAM 약 4.29GiB였고 QA 후 소유 API30 VM만 종료했다.

0.1.5 검증 출력 APK SHA-256:

- 최종 debug universal (fixture versionCode 4006, 혼합 4개): `5A52D8D49AD470A838021595A7D807969DF47C6BF24BC99D3098CA509A60C2C6`
- release x86_64 split (versionCode 4006): `F50B16D9DFEC836CE844E36A790373C4D440F4282F18B8AEC5387778B5069D46`

일반 debug fixture 1개는 앞선 같은 제품 코드의 debug 4005에서 통과했다. 이후 debug 4006 빌드에서 Gradle daemon이 예기치 않게 종료돼 실패했고, 프로젝트 설정을 바꾸지 않는 2GiB heap/768MiB metaspace/작업자 2개/single-use Gradle로 debug/test APK와 lint를 완료했다. 이 최종 debug에서 혼합 4개가 통과했다. API30 시작 전 여유 RAM은 약 1,018MiB여서 기존 기준을 낮추지 않고 VM을 시작하지 않았다. [새 기능 범위](MULTILINGUAL_QA.md).

0.1.6 출력 APK(설치/기기 실행 전) SHA-256:

- debug universal (fixture versionCode 4007): `410614E7F1AB86E2CCD8A6FD1723166B39325056737BBF57E30AAF2C970F0B22`
- release x86_64 split (versionCode 4007): `B12207E19F95776E963AF4247E6054DF3F0DD2A0955B1084CEFA5D438B1A3BBF`
- release instrumentation: `6A5EE8D1E3ED207207AC1C903D993680A7A21B562600011E43CEC66CCC753DF2`

장치 목록은 시작/종료 시 비어 있었고 RAM 2,777MiB로 소유 VM의 3,000MiB 시작 기준에 못 미쳤다. VM/앱/회전 설정을 변경하지 않았다. 가로 패널의 실제 View/서비스/회전·CJK 장문/로그 fixture는 미실행이다. 분석 오류 없음/native 단위 25개/lint 오류0·경고16과 debug/3종 ABI split release/debug·release test APK 빌드만 통과했다. 큰 글꼴/화면 경계는 순수 정책의 회귀이며 기기 실측이 아니다. [대기 중인 명령](QA.md)을 실행할 때 처음에는 새 제품/테스트 APK를 설치하고, 이후에만 해시가 같은 `-ReuseInstalled`를 사용한다.

0.1.6 후속 API30 검사에서는 시작 RAM 약6,201MiB로 기존 기준을 충족해 소유 QA VM을 사용했다. 제품 APK는 위 debug/release 해시 그대로이며 제품/SDK/보호/R8 규칙을 바꾸지 않았다. 최종 release test APK는 `38A6702A2D41FF38A3CB96C71A3740639435400563AC9FD58A31727F8C930BA4`다. 회전 일본어 1개는 같은 native 측정 조건의 이전 진단 fixture에서, 나머지7개는 최종 fixture에서 통과한 개별 결과다. 하나의8개 JUnit 실행이 아니다.

초기 회전 실패는 Flutter 페이지 ScrollView를 카드로 측정한 검사 문제였다. native TextView의 부모로 한정하고 기존 viewport/폭/48dp 제어/출처 조건을 유지해 통과했다. CJK 장문 전체가600자를 넘는 것을 실제 OCR 한 문단의600자 초과로 간주한 초기 quota 검사도 실패했다. 영어의 엄격한 quota 입력은 유지하고 CJK 장문은 `reading-content`로 분리했다. CJK의 `limit=false`는 관찰값이며 모든 원문이 번역됐다는 뜻이 아니다. [상세](MULTILINGUAL_QA.md).

## 격리된 로컬 환경

기존 `emulator-5554`/Medium Phone 및 C:의 프로젝트 SDK 설정을 바꾸지 않았다. C: 여유 공간이 약 3.2GiB여서 큰 이미지/AVD 파일은 별도 D: 작업 폴더에 설치했다.

- QA 루트: `D:\CodexWork\ScreenLingo-QA-01a1efbc`
- 이미지: `sdk\system-images\android-30\google_apis\x86_64`
- AVD 데이터: `avd-api30`
- AVD 이름: `ScreenLingo_API30`
- 포트/장치: `5556` / `emulator-5556`
- AVD 위치 안내 파일: `C:\Users\hanin\.android\avd\ScreenLingo_API30.ini`
- 실행 바이너리: 기존 C: Android SDK의 Emulator 36.6.11.0
- CLI 도구: QA SDK에 고정한 Command-line Tools 17.0, 기존 Android Studio JBR 21 사용

Command-line Tools 23.0의 새 CLI 런타임은 이 컴퓨터에서 초기화되지 않아 사용하지 않았다. 보안 설정이나 정책을 변경하지 않았으며, 기존 JBR을 사용하는 공식 17.0 도구로 QA SDK만 구성했다. 프로젝트 `android/local.properties`의 SDK 경로는 유지했다.

공식 archive `commandlinetools-win-12700392_latest.zip`의 SHA-1은 Google 저장소 메타데이터의 `4a0902fb473afb7ce90a0ff6181d997138a15d9c`와 비교했다. 시스템 이미지는 SDK Manager로 설치했다. [SDK Manager 문서](https://developer.android.com/tools/sdkmanager), [Google 패키지 메타데이터](https://dl.google.com/android/repository/repository2-3.xml).

## 재실행

```powershell
.\scripts\api30-qa.ps1
.\scripts\verify-privacy.ps1 -Serial emulator-5556 -SkipBuild
.\scripts\api30-qa.ps1 -Stop
```

시작 도구는 새 VM을 실행하기 전에 RAM 3GB 이상을 요구하고, 최대 3분 동안 부팅 완료를 확인한다. 해당 포트가 다른 AVD 소유이면 중지하거나 덮어쓰지 않는다. 종료도 AVD 이름을 확인한 뒤 이 QA VM만 대상으로 한다. VM은 창 없이, RAM 1536MB/CPU 2개/SwiftShader/스냅샷 없음/에뮬레이터 사용 통계 없음으로 실행한다. 화면 크기와 밀도는 기존 API 36 비교 장치와 같은 1080×2400/420dpi로 맞춘다. 이는 모든 화면 크기에 대한 검증은 아니다.

`-SkipBuild`는 이미 빌드한 동일 APK를 새 OS에서 검사할 때만 사용한다. 앱/테스트 소스가 APK보다 새롭거나 선택 ABI에 로그 보호 라이브러리가 없으면 시작 전에 거부한다. 실제 사용한 APK 해시와 OS API를 출력한다. SDK/NDK/의존성을 바꾼 경우 생략하지 말고 기본 명령으로 다시 빌드한다.

기본 명령은 `scripts/verify-privacy.ps1 -Serial emulator-5556`이다. debug fixture는 모델이 없으면 Wi-Fi 조건으로 준비하고, release fixture는 매번 새 화면 공유 동의를 진행한다. ABI별 release versionCode가 debug보다 커서 debug fixture 설치에는 ADB의 debuggable 전용 `-d`를 사용한다. 일부 OS에서는 이것만으로 release→debug 다운그레이드를 허용하지 않는다. 따라서 기본 빌드는 설치된 이 앱의 versionCode를 debug 테스트 빌드에 지정한다. 제품 release 빌드는 계속 pubspec의 버전을 사용한다. `-SkipBuild`에서 버전 코드가 거부되면 기본 명령으로 다시 빌드한다. 앱을 삭제하거나 모델 캐시를 초기화하지 않는다. release는 장치 ABI에 맞는 split APK를 우선한다.

## 추가 설치가 필요한 경우

현재 D: 환경은 보존돼 있다. 다른 컴퓨터에 복제할 경우 공식 17.0 도구를 QA SDK에 준비하고 이미 수락한 SDK 라이선스를 해당 SDK의 `licenses`에 준비한 뒤 아래처럼 별도 이미지와 AVD를 만든다. 기존 AVD에 `--force`를 사용하지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& 'D:\CodexWork\ScreenLingo-QA-01a1efbc\sdk\cmdline-tools\17.0\bin\sdkmanager.bat' --sdk_root=D:\CodexWork\ScreenLingo-QA-01a1efbc\sdk --install 'system-images;android-30;google_apis;x86_64'
'no' | & 'D:\CodexWork\ScreenLingo-QA-01a1efbc\sdk\cmdline-tools\17.0\bin\avdmanager.bat' create avd -n ScreenLingo_API30 -k 'system-images;android-30;google_apis;x86_64' -p 'D:\CodexWork\ScreenLingo-QA-01a1efbc\avd-api30' --device pixel_2
```

Android 11의 기본 화면 공유/영어 번역 UI와 0.1.4 생명주기 세 조건/권한 해제 홈 안내는 이 환경에서 완료했다. CJK 새 연습 화면과 12방향 Bitmap은 API30 추가 검증이 필요하다. [다국어 Bitmap fixture 12개 및 실제 release 12개 방향](MULTILINGUAL_QA.md)은 API36에서 통과했다. 자연스러움/긴 문단·혼합 언어, 연속 회전, 시스템 공유 중지 UI, 최근 앱 UI의 제거 제스처, API33/34, OEM/ARM64 장치는 아직 남아 있다.
