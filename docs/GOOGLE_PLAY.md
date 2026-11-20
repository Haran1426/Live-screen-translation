# ScreenLingo Google Play 출시 준비

기준일은 2026-11-21이다. 원문 위에 번역을 표시하는 현재 제품을 기준으로 준비한다.

0.1.12 로컬 검증 번들은 대상 API36과 16KB 번들·네이티브 정렬 검사를 통과했다. 현재 버전의 검증은 정적 검사이며, 제출할 패키지와 업로드 키로 만든 번들을 실제 ARM64 및 16KB 환경에서 검사해야 한다. [대상 API 요건](https://support.google.com/googleplay/android-developer/answer/11926878) · [16KB 호환성](https://developer.android.com/guide/practices/page-sizes)

## 등록 순서

1. 실제 ARM64 장치에서 작성된 연습 화면으로 번역·로그 보호·터치 전달과 화면 공유 종료를 확인한다. 테스트용 앱의 제한은 [릴리스 조건](RELEASE.md)을 따른다.
2. 개발자 계정의 공개 개발자명과 지원 이메일, 최종 패키지 이름을 확인한다. 현재 QA 패키지는 `com.example.livescreentranslation`이다. 예를 들어 `io.github.haran1426.screenlingo`를 후보로 검토할 수 있으며 사용 가능 여부는 Google Play에서 확인한다. 패키지 이름은 첫 등록 전에 확정한다. [앱 등록 도움말](https://support.google.com/googleplay/android-developer/answer/9859152)
3. Play 앱 서명을 설정하고 개발자가 보관하는 업로드 키로 `.aab`를 서명한다. test APK의 개발 인증서와 서명이 다르면 기존 설치에 덮어쓸 수 없다. [앱 서명 안내](https://developer.android.com/studio/publish/app-signing)
4. 개인정보처리방침의 운영자·연락처·시행일을 확정해 로그인 없이 볼 수 있는 공개 웹 주소로 게시한다. 공개된 주소를 Play Console과 제출용 앱에 반영한다. [개인정보처리방침 초안](PRIVACY_POLICY_DRAFT.md) · [Google Play 사용자 데이터 정책](https://support.google.com/googleplay/android-developer/answer/10144311)
5. 앱 콘텐츠의 데이터 보안, 광고, 앱 액세스, 대상 연령, 콘텐츠 등급과 포그라운드 서비스 신고를 마친다. 계정이나 로그인 없이 연습 화면에 접근할 수 있다. 등록 정보는 최종 서명 파일에 맞춰 검토한다.
6. 계정 상태에 맞춰 필요한 테스트를 진행한다. 2023-11-13 이후 만든 개인 개발자 계정은 12명 이상이 14일 이상 연속 참여한 비공개 테스트를 거친 뒤 정식 출시 권한을 신청한다. [개인 계정 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465)

## 스토어 설명 초안

**앱 이름**

ScreenLingo - 화면 번역

**짧은 설명**

화면 글자를 읽어 원문 위치에 번역을 겹쳐 보여줍니다.

**전체 설명**

ScreenLingo로 Android 화면의 외국어를 읽어 보세요. 화면 공유를 시작하면 번역이 원문 글자 위치에 나타납니다. 번역이 길거나 원문 자리에 들어가지 않으면 동그란 버튼을 눌러 카드에서 읽을 수 있습니다.

영어, 한국어, 일본어, 중국어를 지원합니다. 처음 사용할 때 언어 모델을 준비하면 화면 인식과 번역은 기기에서 처리합니다. 번역을 시작할 때마다 Android 화면 공유에 직접 동의하고, 카드에서 일시정지하거나 종료할 수 있습니다.

Android 11 이상에서 사용할 수 있습니다. 보안 화면이나 작은 글자는 읽기 어려울 수 있으며 자동 번역 결과는 원문의 문맥에 따라 달라집니다.

**이미지**

기존 앱 아이콘을 바탕으로 512×512 아이콘과 1024×500 그래픽을 준비한다. 실제 UI가 담긴 휴대폰 스크린샷에 작성된 연습 문장만 사용한다. 보안 창 때문에 번역이 캡처되지 않으면 창 보호를 끄거나 모의 결과를 붙이지 않는다. [등록 이미지 요구사항](https://support.google.com/googleplay/android-developer/answer/9866151)

## 화면 공유 서비스 신고 초안

**기능 용도**

사용자가 허용한 Android 화면 공유 세션 동안 화면의 글자를 인식하고 원문 위치에 번역을 표시합니다. 화면 공유를 계속하는 동안 포그라운드 서비스를 사용합니다. 시작은 앱의 버튼과 Android 시스템 동의로 실행하고, 앱 카드 또는 알림의 종료 동작으로 끝냅니다.

**지연 또는 중단 영향**

시작이 늦어지면 현재 화면의 글자를 읽거나 번역을 표시할 수 없습니다. 시스템이 공유를 중단하면 번역과 표시도 중단됩니다. 다시 사용하려면 사용자가 새 화면 공유에 동의해야 합니다.

**심사 영상 순서**

언어 모델 준비와 표시 권한 안내 → 번역 시작 → 앱 안내와 Android 시스템 화면 공유 동의 → 작성한 영어 연습 화면의 한국어 번역과 화면 공유 상태 → 카드 열기 및 종료 → 번역과 공유 표시가 사라지는 화면. 보안 창을 실제 기기에서 보여줄 필요가 있으면 테스트폰을 외부 카메라로 촬영한다. [포그라운드 서비스 신고](https://support.google.com/googleplay/android-developer/answer/13392821)

## 데이터 보안 답안 검토

| 확인 영역 | 현재 구현과 검토 근거 |
| --- | --- |
| 화면 이미지·원문·번역 | 기기 안에서 처리하고 파일이나 서버에 저장하지 않는다. 원본과 번역문을 SDK 진단 정보와 구분한다. 실제 기기 로그 보호 확인이 남아 있다. |
| ML Kit | 선택한 출발·도착 언어, 앱과 기기 정보, 설치 식별자, 처리 시간·오류·사용량 등 진단 데이터를 Google에 전송할 수 있다. 앱 정보 및 성능, 앱 활동, 기기 또는 기타 ID 범주를 폼에서 확인한다. |
| SDK의 Firebase 구성요소 | ML Kit Translate 문서가 Remote Config 및 Installations 사용을 명시한다. 설치·기기 구성 관련 수집 항목을 해당 고지와 최종 APK 의존성에 맞춰 확인한다. |
| 계정·광고 | 현재 앱에는 자체 계정이나 광고가 없다. Google 서비스에 의한 수집까지 없다고 표시하는 근거로 쓰지 않는다. |

데이터 수집·공유 여부의 최종 답은 배포 버전과 Google Play의 항목 정의를 기준으로 정한다. [ML Kit 데이터 공개](https://developers.google.com/ml-kit/android-data-disclosure) · [Firebase 데이터 공개](https://firebase.google.com/docs/android/play-data-disclosure)
