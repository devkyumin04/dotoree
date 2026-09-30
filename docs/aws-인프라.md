# AWS 인프라 기록

AWS 콘솔에서 **무엇을 어떤 값으로 만들었고 왜 그랬는지**. 콘솔 클릭은 이력이 안 남아서 여기에 남긴다.
실무는 이런 자원을 코드(Terraform·CDK)로 만들어 PR 로 리뷰한다 — 한 대짜리 개인 프로젝트라 콘솔 + 이 문서로 대신한다.
"왜"의 정본은 ADR-002 보완 · ADR-042, 단계 진행은 진행상황.md "1차 배포". 이 문서는 **지금 AWS 에 무엇이 있는가 + 자주 하는 조작**.

> **값은 적지 않는다** (CLAUDE.md "비밀값 · 개인정보"). IP·pem 위치·DB 비밀번호 같은 값은 **저장소 밖 개인 보관처**에. 여기엔 `<EC2_HOST>` 처럼 이름만.

---

## 지금 있는 것 (2026-09-17 기준)

| 자원 | 이름 / 값 | 비고 |
|---|---|---|
| 리전 | 서울 `ap-northeast-2` | 콘솔 오른쪽 위가 서울인지 먼저 확인. 다른 리전이면 인스턴스가 안 보인다 |
| EC2 인스턴스 | `ledger-prod` | Ubuntu Server 26.04 LTS, **arm64**, 접속 계정 `ubuntu` |
| 인스턴스 유형 | **t4g.small** (2vCPU · 2GB) | $0.0208/시간. medium 으로 만들었다가 같은 날 내림. Ollama 설치 직전에 t4g.medium 으로 (로드맵) |
| 크레딧 사양 | **Standard** | 기본값 Unlimited 에서 변경. CPU 를 오래 써도 추가 요금 없음 (대신 크레딧이 떨어지면 느려짐) |
| 디스크 | 30GB gp3 | 기본 8GB 에서 변경. **늘릴 수는 있어도 줄일 수는 없다** |
| 키 페어 (내 접속용) | `ledger-admin` (ED25519, `.pem`) | AWS 가 생성. 다운로드는 생성 시 1회뿐 — 잃어버리면 재발급 불가 |
| 키 페어 (CD 배포용) | `ledger-deploy` (ED25519, 2026-09-18) | **AWS 키 페어가 아니라 맥에서 직접 생성**(`ssh-keygen`). 공개키는 `deploy` 계정 `authorized_keys`, 개인키는 GitHub Secrets. passphrase 없음. 유출되면 새로 만들어 갈아 끼우면 된다 |
| 보안 그룹 | `launch-wizard-1` | 인바운드 22 = **내 IP 만** / 80 · 443 = 전체. 8080 · 3306 은 열지 않음 |
| 탄력적 IP | 1개, `ledger-prod` 에 연결 | 재시작해도 IP 고정. **인스턴스 없이 혼자 남으면 그것대로 과금** — 인스턴스를 지울 땐 같이 릴리스 |
| MySQL | **8.4.11** (우분투 패키지, 2026-09-17 설치) | `ledger_db` + 앱 계정 `ledger_app`@`localhost`. root 는 `auth_socket`(비번 없음, 소켓 인증). 3306 은 `127.0.0.1` 바인딩 |
| Java | **17.0.20** (`openjdk-17-jre-headless`, arm64) | JRE 만 — 빌드는 맥·CI, 서버는 실행만 |
| 앱 실행 유저 | `ledger` (UID 999) | 시스템 계정 · 셸 `nologin` · 비번 없음(`!`) · 홈 없음. systemd `User=` 로 쓴다 (ADR-043 과 같은 판단) |
| 배포 계정 | `deploy` (2026-09-18) | CD 전용. 셸 `/bin/bash`(SSH 로 들어와 jar 를 놓아야 하므로) · 비번 잠김, 키만 · `/opt/ledger` 소유 · sudo 는 `systemctl restart ledger` **한 줄만**(`/etc/sudoers.d/ledger-deploy`). `ledger.env` 는 못 읽는다 |
| 앱 서비스 | `ledger.service` (systemd, 2026-09-18) | **`active (running)`** · `enable` 됨 · `Restart=always`. jar 는 `/opt/ledger/ledger.jar`(고정 이름), 비밀값은 `/etc/ledger/ledger.env`(root 600). 2-5 에서 첫 배포·재부팅 자동 기동 확인 |
| OS 타임존 | `Asia/Seoul` | MySQL 설치 **전에** 바꿨다. MySQL `time_zone=SYSTEM` 이 OS 를 따라가므로 순서가 중요 (ADR-026) |
| IAM ID 제공업체 | `token.actions.githubusercontent.com` (OIDC, 2026-09-19) | GitHub Actions 가 발급한 토큰을 AWS 가 검증하도록 등록. 대상(Audience) `sts.amazonaws.com`. **이것만으론 권한이 0** — 권한은 역할에 붙는다 |
| IAM 정책 | `ledger-deploy-sg-ssh` (2026-09-19) | `ec2:AuthorizeSecurityGroupIngress` · `ec2:RevokeSecurityGroupIngress` **두 개만**, 리소스는 `launch-wizard-1` 보안그룹 하나의 ARN. 포트까지는 못 좁힌다(IAM 조건 키에 포트가 없음) — 진행상황.md 3-4 의 감수 |
| IAM 역할 | `ledger-github-deploy` (2026-09-19, 신뢰 정책 2026-09-20 수정, **2026-09-30 저장소 이름 `dotoree` 로**) | GitHub Actions 가 OIDC 로 맡는 역할. 신뢰 정책 `sub` 는 **main 브랜치 두 형태만**(`repo:<OWNER>/<REPO>:ref:refs/heads/main` + 숫자 ID 가 박힌 `repo:<OWNER>@<OWNER_ID>/<REPO>@<REPO_ID>:...`). **실제로 오는 건 ID 형태** — 콘솔이 자동으로 채워주는 이름 형태만 두면 `Not authorized` 로 거절된다(3-6 실측). 권한은 `ledger-deploy-sg-ssh` 하나 |
| 도메인 | **`dotoree.app`** (2026-09-21, Cloudflare Registrar) | 1년 · 자동 갱신 켬 · 연 $14.20(원가 판매라 갱신가 동일). 네임서버는 Cloudflare 고정(이 등록기관의 조건). **AWS 자원이 아니라 Cloudflare 계정**(2FA TOTP)에 있다. `.app` 은 HSTS preload — 인증서 없이는 브라우저로 안 열린다 |
| DNS 레코드 | `A @` → 탄력적 IP / `CNAME www` → `dotoree.app` | 둘 다 **DNS only(회색 구름)**. 프록시를 켜면 HTTPS 를 Cloudflare 가 대신 끝내서 Nginx·certbot 이 할 일이 사라지고 인증도 가로막힌다. `www` 를 CNAME 으로 둔 건 IP 가 바뀔 때 A 한 줄만 고치려고(ETC) |
| Nginx | **1.28.3** (apt, 2026-09-21) | `/etc/nginx/sites-available/ledger`(→ `sites-enabled` 링크). 블록 4개 — 80·443 문지기(모르는 이름은 444 / TLS 거부) · 80→443 · 443→`127.0.0.1:8080`. `/actuator` 는 health 만 통과, 나머지 404. certbot 이 고쳤던 원본 백업 `ledger.bak-certbot` 은 2026-09-23 삭제(재작성 설정이 이틀 문제없어서) |
| TLS 인증서 | Let's Encrypt, `dotoree.app` + `www` (2026-09-21) | certbot **4.0.0**(apt). `/etc/letsencrypt/live/dotoree.app/`(개인키 root 전용). 90일 — `certbot.timer` 가 만료 30일 이내일 때 갱신. 첫 만료 2026-12-20 |
| DB 백업 (로컬) | `/usr/local/sbin/ledger-backup` + `ledger-backup.timer` (2026-09-22) | 매일 **04:00 KST**, `Persistent=true`(꺼져 있던 날은 켜지면 밀린 실행). root 가 `mysqldump --single-transaction`(MySQL root = `auth_socket`, 새 비밀 0개) → gzip → `/var/backups/ledger/`(root 700 · 파일 600). `.tmp` 에 쓰고 검사 두 개(`gzip -t` + 마지막 줄 `-- Dump completed`) 통과해야 `mv`. 보관 **약 7일**(`-mtime +7` 이라 7~8개). 로그 `journalctl -u ledger-backup`. 성공하면 S3 `daily/` 로 업로드(인스턴스 역할, 키 파일 없음) — **업로드가 실패하면 로컬 정리 전에 멈춘다.** 끝나면 성공이든 실패든 Healthchecks `ledger-backup` 에 핑(2026-09-22, ADR-051). 정본은 저장소 `ops/`(sha256 일치 확인) |
| 백업 설정 파일 | `/etc/ledger/backup.env` (root 600, 2026-09-22) | 세 줄 — `S3_BACKUP_BUCKET`(식별자. 손으로 쓰지 않고 `sts get-caller-identity` 로 조립, 트러블슈팅 18) · `HC_BACKUP_URL` · `HC_CERT_URL`(약한 비밀. `read -s` 로 받아 정규식·길이 검사 후 `tee -a`). 백업·인증서 스크립트가 같이 읽는다 |
| AWS CLI | `aws-cli` **2.35.21** (snap, `aws✓` 게시자, classic, 2026-09-22) | Ubuntu 26.04 apt 에 v2 가 없어 snap(AWS 공식, 자동 업데이트). **`/snap/bin/aws` 전체 경로로** — systemd PATH 에 `/snap/bin` 이 없다. `~/.aws` 없음 = 키 파일 없이 역할로 인증 |
| S3 버킷 (백업) | `<S3_BACKUP_BUCKET>` (2026-09-22) | 서울 · 범용 · **계정 리전 네임스페이스**(이름 끝에 계정 ID·리전·`-an` — 우리 계정만 이 이름을 만들 수 있고 지워도 남이 못 가져간다. 그래서 랜덤 접미사 없음) · 퍼블릭 액세스 차단 4개 전부 · ACL 비활성화 · **버전 관리 ON**(덮어쓰기로 백업을 없애는 것 차단) · SSE-S3 · 객체 잠금 OFF · 태그 없음. 이름은 식별자(ADR-048) — 실제 값은 개인 보관처 · EC2 `backup.env` |
| S3 수명 주기 규칙 | `daily-expire` (2026-09-22) | 범위 접두사 `daily/` 만(다른 폴더는 따로 규칙) · 현재 버전 **30일** 만료 · 이전 버전 **7일** 뒤 영구 삭제(보관할 버전 수 비움) · 불완전 멀티파트 7일 삭제. 평소 파일 수명 최대 37일. "만료된 삭제 마커 삭제"는 현재 버전 만료와 같이 못 고른다(콘솔이 막음) |
| IAM 정책 | `ledger-backup-s3-put` (2026-09-22) | `s3:PutObject` **하나만**, 리소스 `arn:aws:s3:::<S3_BACKUP_BUCKET>/daily/*`. 읽기·목록·삭제 없음 — 서버가 털려도 백업을 꺼내거나 지울 수 없다. `ListBucket` 은 올리기에 필요 없어 뺐다 |
| IAM 역할 (인스턴스) | `ledger-prod-ec2` (2026-09-22) | 신뢰 주체 `ec2.amazonaws.com`, 권한 `ledger-backup-s3-put` 하나. `ledger-prod` 에 연결(재시작 없음). **이름은 인스턴스 기준** — EC2 한 대엔 역할이 하나뿐이라, SES 등 권한이 늘면 새 역할이 아니라 이 역할에 정책을 추가(정책 = 일 단위). 같은 이름의 인스턴스 프로파일은 콘솔이 자동 생성 |
| 인스턴스 메타데이터 | **IMDSv2 Required** (2026-09-22 확인) | 역할의 임시 자격 증명을 받는 경로. v1 이면 앱의 SSRF 하나로 자격 증명이 밖으로 샌다 — 역할을 붙이는 순간부터 중요해져서 확인 |
| 인증서 검사 | `/usr/local/sbin/ledger-certcheck` + `ledger-certcheck.timer` (2026-09-22) | 매일 **09:00 KST**, `Persistent`. `openssl s_client` 로 `127.0.0.1:443`(SNI `dotoree.app`)에 붙어 **지금 내보내는 인증서**의 남은 날짜 → 21일 초과면 Healthchecks `ledger-cert` 에 `/0`, 아니면 `/1`(본문에 N days left). 로그 `journalctl -u ledger-certcheck`. 정본은 저장소 `ops/` (ADR-051) |
| 외부 감시 | **UptimeRobot** 무료 (2026-09-22, AWS 밖) | 키워드 모니터 `ledger-health` — `https://dotoree.app/actuator/health` 본문에 `"status":"UP"` 이 **없으면** DOWN, 5분 간격, 북미에서. 알림 이메일만. SSL 만료·heartbeat·상태 코드 선택은 유료라 안 씀(트러블슈팅 20). 계정 2FA(TOTP) |
| 작업 감시 | **Healthchecks.io** 무료 (2026-09-22, AWS 밖) | 체크 `ledger-backup` · `ledger-cert` — 둘 다 Period 1일 + Grace 1시간, 알림 이메일. 실패 핑은 즉시, 무응답은 25시간 뒤 메일. 계정 2FA(TOTP, 복구 코드 없음), 시간대 Asia/Seoul |
| SES 발신 도메인 | `dotoree.app` 도메인 자격 증명 (2026-09-22, 서울) | **도메인 단위** — `noreply@`·`support@` 등 이 도메인의 어떤 주소로든 보낸다(이메일 주소 단위는 그 주소 하나만). **Easy DKIM**(RSA 2048) — SES 가 준 CNAME 3개를 Cloudflare 에(DNS only, 이름은 `…._domainkey` 까지만 — Cloudflare 가 도메인을 붙인다). Route 53 자동 게시는 끔(DNS 가 Cloudflare). 사용자 지정 MAIL FROM 없음 — DMARC 정렬은 DKIM 으로 충족. 기본 구성 세트·테넌트 없음. **발송 권한(역할 정책)은 아직 안 붙임** — 이메일 인증 기능을 만들 때 `ledger-prod-ec2` 에 정책 추가. 계정은 **샌드박스**(검증된 수신자만 · 하루 200통 · 초당 1통) → 해제 신청은 아래 이력 |
| ~~메일 받기 (support@)~~ | ~~Cloudflare **Email Routing** (2026-09-23, AWS 밖)~~ → **2026-09-30 SES 수신으로 교체(아래). Email Routing 은 끔 — 도메인 온보딩 해제, Cloudflare 가 넣었던 MX 3줄·SPF TXT·DKIM `cf2024-1` 삭제 확인.** 새 DNS: `MX @ 10 inbound-smtp.ap-northeast-2.amazonaws.com`(DNS only). 루트 SPF TXT 는 지금 없음(받기엔 불필요, 보내기는 SES 가 amazonses.com MAIL FROM 으로 처리) | `support@dotoree.app` → 개인 Gmail 로 전달(받기 전용, 보내기는 SES). 계정 단위 메뉴 Compute → Email Service → Email Routing. Cloudflare 가 MX 3줄(`route1~3.mx.cloudflare.net`) · 루트 SPF TXT(`include:_spf.mx.cloudflare.net`) · DKIM TXT(`cf2024-1._domainkey`)를 추가. SES 는 SPF 를 amazonses.com 으로 처리하고 DKIM 이름이 달라 충돌 없음. 전달 대상 주소는 저장소에 적지 않는다 |
| S3 버킷 (문의 메일함) | `<S3_MAIL_BUCKET>` = `ledger-mail` + 계정 리전 접미사 (2026-09-30) | 서울 · 범용 · **계정 리전 네임스페이스**(백업 버킷과 같은 방식) · 퍼블릭 전부 차단 · 버전 관리 **끔**(메일은 고칠 일이 없다) · SSE-S3 · ACL 비활성. **왜** — 문의 메일을 Gmail(미국)이 아니라 **국내(서울)에 보관**해 국외 이전 자체를 없앤다(처리방침·시행령 제29조의10 DPA 문제, 진행상황 "DPA 재검토") |
| S3 수명 주기 규칙 | `inbox-expire` (2026-09-30) | 접두사 `inbox/` · 현재 버전 **90일** 만료 · 불완전 멀티파트 7일. 처리방침의 보관 기간과 맞물려 **수동 삭제가 필요 없다** |
| S3 버킷 정책 | `AllowSESPutsFromLedgerInbound` (2026-09-30) | 주체 `ses.amazonaws.com` 만 `s3:PutObject`, 리소스 `inbox/*`, 조건 `aws:SourceAccount` = 우리 계정 + `ArnLike aws:SourceArn` = `receipt-rule-set/ledger-inbound:receipt-rule/*`. 다른 계정의 SES 가 우리 버킷에 쓰는 것(confused deputy)을 막는다. 콘솔 정책 검사기가 `StringLike` → `ArnLike` 를 권해 그대로 |
| SES 수신 규칙 세트 | `ledger-inbound` — **활성** (2026-09-30, 서울) | 규칙 `support-to-s3`: 수신자 **`support@dotoree.app` 하나만**(다른 주소로 온 메일은 거절) · 스팸·바이러스 검사 켬 · TLS 선택(필수로 하면 TLS 없는 발신 서버 메일이 통째로 거절) · 작업 S3 저장 `<S3_MAIL_BUCKET>/inbox/`(파일 이름은 SES 의 무작위 ID). **SES 자체 SNS 알림은 안 붙임** — 그 알림엔 보낸 사람 주소·제목이 담겨 Gmail 로 가면 국외 이전이 되살아난다. 알림은 S3 이벤트(파일 이름만)로 따로. 생성 직후 SES 가 `inbox/AMAZON_SES_SETUP_NOTIFICATION` 을 써서 권한을 확인한다. **MX 를 SES 로 돌리기 전까지는 메일이 오지 않는다**(Cloudflare 3단계) |
| SNS 주제 (새 메일 알림) | `ledger-mail-notify` (2026-09-30, 서울, 표준) | 주제 정책 — 기본 문(소유 계정만) + `AllowS3MailBucketPublish`: 주체 `s3.amazonaws.com` 만 `SNS:Publish`, 조건 `aws:SourceAccount` = 우리 계정 + `ArnLike aws:SourceArn` = `<S3_MAIL_BUCKET>`. 구독 1개 — 이메일(개인 메일, 주소는 저장소에 적지 않는다), **구독 확인 링크를 눌러야 동작** |
| S3 이벤트 알림 | `inbox-new-mail` (2026-09-30) | `<S3_MAIL_BUCKET>` · 접두사 `inbox/` · `s3:ObjectCreated:Put` → `ledger-mail-notify`. **왜 SES 의 SNS 알림이 아니라 S3 이벤트인가** — SES 알림엔 보낸 사람 주소·제목이 들어가 개인 메일(Gmail, 미국)로 가면 국외 이전이 되살아난다. S3 이벤트 JSON 엔 버킷·파일 이름(무작위 ID)·크기·시각뿐 |
| DMARC | `_dmarc` TXT `v=DMARC1; p=none;` (2026-09-22, Cloudflare) | 인증 실패 메일을 **막지 않고 보기만**. 설정 실수로 우리 메일까지 사라지지 않게 `none` 으로 시작하고, 메일이 몇 주 문제없이 나가면 `quarantine` 으로 올린다. 보고서 수신(`rua`)은 받을 메일함이 생기면 |
| 예산 알림 | `ledger-monthly` 월 $30 | 실제 85% · 100% 도달, 예상 100% 도달 시 메일. **알림만 — 과금을 멈추지는 않는다** |

아직 없는 것 — SES 발송 권한·앱 설정(Sprint 1 이메일 인증 때).

## 비용 (월 추정)

730시간 + 디스크 30GB + 공인 IPv4, VAT 10% 포함, 환율 1,400원 가정. 시간당 가격은 콘솔 표시가(서울).

| 유형 | 메모리 | 시간당 | 월 |
|---|---|---|---|
| t4g.micro | 1GB | $0.0104 | 약 2.2만원 — 앱 + MySQL 로 꽉 차서 탈락 |
| **t4g.small (지금)** | 2GB | $0.0208 | **약 3.3만원** |
| t4g.medium | 4GB | $0.0416 | 약 5.7만원 — Ollama 부터 |
| t3.medium (원래 계획, x86) | 4GB | $0.052 | 약 6.8만원 |

인스턴스 외 고정비 — 디스크 약 $2.7 + 공인 IPv4 약 $3.65. **인스턴스를 중지해도 이 둘은 계속 나간다.**

### 중지하면 과금이 멈추나

| 상태 | 인스턴스(시간당) | 디스크 30GB | 탄력적 IP | 월 |
|---|---|---|---|---|
| 실행 중 (t4g.small) | 과금 (초 단위, 쓴 만큼) | 과금 | 과금 | 약 3.3만원 |
| **중지** | **0원** | 과금 | 과금 | 약 1만원 |
| 종료(삭제) + IP 릴리스 | 0원 | 0원 | 0원 | 0원 — 서버가 사라진다 |

- 중지는 "전원 끄기" — 디스크 내용(설치한 MySQL·데이터)은 그대로, 다시 시작하면 이어진다. 탄력적 IP 라 주소도 같다
- 오래 자리를 비울 때(며칠 이상) 중지해 두면 하루 약 700원이 준다. 단 24시간 운영이 목표라 **배포 URL 을 공개한 뒤엔 끄지 않는다** (UptimeRobot 이 장애로 본다)

## 왜 이렇게 골랐나 (면접용 한 줄씩)

- **ARM(t4g)** — 같은 사양에 x86 보다 약 20% 싸다. jar 는 아키텍처 무관이고 MySQL · Nginx · Ollama 가 arm64 를 공식 지원해서 묶이는 게 없다. 단 **ARM ↔ x86 은 유형 변경이 안 되고 새로 만들어야 한다** — 처음에 정해야 하는 유일한 것
- **small 로 시작** — 지금 올라가는 건 Spring Boot + MySQL 뿐. 4GB 는 Ollama 부터 필요한데 그 시점을 모른다. 유형 변경이 5분(실측)이라 미리 낼 이유가 없다
- **22번은 내 IP 만** — 전체로 열면 SSH 무차별 대입 시도가 바로 들어온다. 키 인증이라 뚫리진 않아도 로그가 오염되고 공격면이 넓어진다
- **80 · 443 만 열고 8080 은 닫음** — 사용자는 포트 없이 접속한다(http=80, https=443). Nginx 가 받아 `localhost:8080` 으로 넘긴다. 앱이 인터넷에 직접 노출되지 않아 `/actuator` 같은 경로를 밖에서 직접 찌를 수 없다
- **3306 은 닫음** — DB 는 같은 인스턴스의 앱만 접속(`localhost`). 밖에서 DB 를 볼 일이 있으면 SSH 터널
- **Standard 크레딧** — 예산이 고정인 개인 프로젝트는 "느려지는 것"이 "요금이 튀는 것"보다 낫다
- **Ubuntu 26.04 LTS** — MySQL 8.0 이 2026-04 지원 종료. 새 운영 서버는 최신 LTS 패키지를 쓴다
- **탄력적 IP** — 도메인 · GitHub Secrets(`EC2_HOST`) · 카카오 콘솔이 가리킬 주소가 재시작마다 바뀌면 안 된다

## 자주 하는 조작

### 접속

```
ssh -i <PEM_PATH> ubuntu@<EC2_HOST>
```

실제로는 `~/private/` 의 서버 접속 `.command`(더블클릭하면 위 명령이 실행된다)로 접속한다. `~/.ssh/config` 에 `Host` 는 등록하지 않았다(2026-09-22 확인) — 그래서 파일 전송도 이름 대신 직접 지정한다:

```
scp -i ~/.ssh/ledger-admin.pem <파일…> ubuntu@<EC2_HOST>:/tmp/     # 맥에서. 서버에선 sudo install 로 제자리에
```

자주 쓰게 되면 `~/.ssh/config` 에 `Host ledger`(HostName · User · IdentityFile · `IdentitiesOnly yes`)를 등록해 `ssh ledger` / `scp … ledger:` 로 줄인다. 저장소 밖이라 IP 를 적어도 된다.

| 증상 | 원인 | 해결 |
|---|---|---|
| 반응 없다가 `timed out` | 집 IP 가 바뀜 (공유기 재시작, 다른 장소) | EC2 → 보안 그룹 → 인바운드 규칙 편집 → 22번 소스를 "내 IP" 로 다시 |
| `UNPROTECTED PRIVATE KEY FILE` | pem 권한이 넓음 | `chmod 400 <PEM_PATH>` |
| `Permission denied (publickey)` | 계정 · 키 경로 오타 | 계정은 `ubuntu`, `-i` 경로 확인 |
| `REMOTE HOST IDENTIFICATION HAS CHANGED` | 인스턴스를 새로 만들어 같은 IP 에 다른 서버 | `ssh-keygen -R <EC2_HOST>` 후 재접속 |

### MySQL 접속 · 확인

```
sudo mysql                      # root, 비번 없이 (auth_socket — sudo 가 곧 인증)
mysql -u ledger_app -p ledger_db   # 앱 계정으로
```

| 확인할 것 | 명령 | 기대 |
|---|---|---|
| 타임존 | `SELECT @@global.time_zone, NOW();` | `SYSTEM` + 한국 시각 |
| 앱 계정 권한 | `SHOW GRANTS FOR 'ledger_app'@'localhost';` | `USAGE ON *.*` + `ALL PRIVILEGES ON ledger_db.*` 두 줄만 |
| collation | `SHOW CREATE DATABASE ledger_db;` | `utf8mb4_0900_ai_ci` (맥·CI 와 같은 값, ADR-044) |
| 테이블 | `USE ledger_db; SHOW TABLES;` | 19개 — V1 의 18개 + `flyway_schema_history`. **이름은 전부 소문자** (ADR-045) |
| 메모리 | `free -h` | available 300Mi 아래면 스왑 검토 |

### DBeaver 로 운영 DB 보기 (SSH 터널)

**3306 을 열지 않고도 본다.** 보안 그룹에 3306 이 없고 MySQL 도 `127.0.0.1` 바인딩이지만,
SSH 로 먼저 서버에 들어간 뒤 그 통로 안에서 `localhost:3306` 에 붙으면 된다.
결과적으로 DB 에 닿으려면 **pem 키(가진 것) + DB 비번(아는 것)** 둘 다 필요해진다.

MySQL 커넥션을 새로 만들고 **두 탭을 나눠** 채운다 — SSH 탭은 "서버까지 가는 길", Main 탭은 "도착해서 누구로 어디에 붙나".

| 탭 | 항목 | 값 |
|---|---|---|
| SSH (`+ SSH, SSL, ...` 로 추가) | Host / Port | `<EC2_HOST>` / `22` |
| | User Name | `ubuntu` |
| | Authentication Method | Public Key → Private Key 에 `<PEM_PATH>` |
| Main | Server Host / Port | **`127.0.0.1`** / `3306` (서버 안에서 본 주소) |
| | Database | `ledger_db` |
| | Username / Password | `ledger_app` / `DB_PASSWORD` 값 |

- **root 로는 못 붙는다** — `auth_socket` 이라 비번 자체를 받지 않는다 (ADR-043). 관리자 작업은 SSH 에서 `sudo mysql`
- 확인 순서 — SSH 탭의 `Test tunnel configuration`(Connected) → 왼쪽 아래 `Test Connection`(8.4.11 표시)
- **커넥션 이름은 `ledger_db(EC2)`, Connection type 은 Production** 으로(2026-09-23 화면에선 **Development 로 되어 있었다** — 커넥션 편집 → General → Connection type 에서 바꿀 것). 탭이 빨개지고 DELETE·UPDATE 에 확인창이 뜬다.
  로컬 `ledger_db` 와 헷갈려 운영 데이터를 지우는 사고를 막는 가장 싼 장치다
- **읽기 전용 + 수동 커밋 — 안전 장치** (이전 세션에서 설정, 2026-09-25 화면으로 확인. 빨간 탭도 확인 = Production 반영됨)
  - 설정 위치 — 커넥션 편집 → General → Security 의 `Read-only connection` / 툴바 트랜잭션 모드는 Manual commit(툴바 버튼이 `Switch to auto-commit` 으로 보이면 수동). 켜져 있으면 툴바에 주황 자물쇠 · `Commit`·`Rollback` 버튼 · 트랜잭션 카운터가 보인다
  - 왜 — 운영 데이터는 실수로 바뀌면 되돌릴 길이 백업 복구뿐이다. **읽기 전용**이면 UPDATE·DELETE 를 아예 못 보내고, **수동 커밋**이면 쓰기가 필요해 자물쇠를 잠깐 풀었을 때도 `Commit` 전까지는 `Rollback` 으로 되돌릴 수 있다. 쓸 일이 있을 때만(예: 2026-09-23 확인용 계정 삭제) 자물쇠를 풀고, 끝나면 다시 잠근다
  - **주의 — 조회만 해도 트랜잭션이 열린다.** 수동 커밋에선 SELECT 도 트랜잭션을 시작하고(카운터 `1`), 끝낼 때까지 그 테이블에 "사용 중" 표시(메타데이터 잠금)가 남는다 → 평소 앱의 조회·저장은 막히지 않지만, **배포 때 Flyway 의 `ALTER TABLE` 은 그 표시가 풀릴 때까지 기다려서 새 앱이 안 뜬다.** 기다리는 ALTER 뒤로 그 테이블의 일반 요청까지 줄을 서서 테이블이 통째로 멈춘다. **조회 뒤엔 `Rollback`(카운터 `None` 확인) 또는 Disconnect, 배포 전엔 EC2 커넥션을 끊어 둔다** (2026-09-25 V3 준비 중 발견)
- 집 IP 가 바뀌면 이 터널도 SSH 와 같이 타임아웃 난다 → 보안 그룹 22번 소스를 "내 IP" 로 다시
- 이 방식은 나중에 서버가 늘거나 DB 를 밖으로 빼도 유지된다 — 사람은 터널·배스천을 거치고, 3306 은 앱 서버에만 연다

#### 운영 DB 를 만질 때 — 체크리스트 (2026-09-25)

**조회만 할 때**
1. 탭이 **빨간색**인지 본다 — 운영(`ledger_db(EC2)`)과 로컬을 헷갈리지 않게
2. 툴바 **자물쇠가 잠겨 있는지**(주황) 본다 — 읽기 전용
3. 조회가 끝나면 **`Rollback`** → 카운터가 **`None`** 인지 확인 (SELECT 도 트랜잭션을 연다)
4. 다 봤으면 **Disconnect** — 연결 수(커넥션 풀) 때문이 아니다. 끊으면 MySQL 이 그 연결의 트랜잭션·잠금을 전부 정리하고(Rollback 을 잊었을 때의 마지막 안전망), SSH 터널도 같이 닫히고, 운영 탭에서 잘못 실행할 기회가 없어진다
5. 결과를 문서·커밋에 옮길 땐 값을 가린다 — 실사용자 이메일·데이터, 서버 IP(DBeaver 왼쪽 목록에 보인다)

**데이터를 바꿀 때 (UPDATE · DELETE)**
1. **오늘 백업이 성공했는지** 먼저 본다 (Healthchecks.io `ledger-backup` up) — 되돌릴 마지막 수단
2. 같은 `WHERE` 로 **SELECT 먼저** → 바뀔 행 수를 적어 둔다
3. 자물쇠를 풀고 실행 → **영향받은 행 수가 2번과 같으면 `Commit`, 다르면 `Rollback`**
4. 자물쇠를 다시 잠그고 Disconnect
5. 이 문서 "작업 이력"에 한 줄

**하지 않는 것**
- **스키마 변경(`ALTER` · `CREATE` · `DROP`)을 DBeaver 로 직접 하지 않는다** — 반드시 Flyway `V` 파일로. 손으로 바꾸면 `flyway_schema_history` 와 실제 DB 가 어긋나고, 로컬·CI 에는 없는 변경이 운영에만 남는다
- **배포 전엔 EC2 커넥션을 끊어 둔다** — 열린 트랜잭션이 Flyway 의 `ALTER` 를 멈춰 새 앱이 안 뜬다(위 "주의")

**알아 둘 것 — Production 확인 창 (2026-09-25)**
- `SELECT` 는 바로 실행되지만 `SHOW CREATE TABLE` 처럼 **SELECT 가 아닌 명령은 "Confirm query execution" 창**이 뜬다(연결 유형이 Production 이라서). 이 창이 DBeaver 창 **뒤에 숨으면** 쿼리가 멈춘 것처럼 진행 막대만 돈다 → 창을 찾아 Yes/No. "Do not ask" 는 켜지 않는다(실수 방지 장치)

- 앱 계정 비밀번호는 해시로만 저장돼 **다시 꺼내볼 수 없다.** 잃어버리면 `ALTER USER 'ledger_app'@'localhost' IDENTIFIED BY '새 값';` 로 재설정하고 systemd `EnvironmentFile` 도 같이 고친다
- root 비번을 만들지 않은 이유는 ADR-043

### 커밋 · push 할 때 — 체크리스트 (2026-09-25)

**main 에 코드를 push 하면 곧 운영 배포다** — CI `build → qa → deploy`, 배포 중 약 10초 중단.
문서만 바꾼 push(`docs/**`, `**.md`)는 CI 가 안 돌아 배포도 없다 (ci.yml `paths-ignore`)

**커밋 전 (모든 커밋)**
1. `git status` — 의도한 파일만 들어가는가. `docs/로컬환경.md` · pem · `.env` 가 없는가
2. 비밀값·식별자가 없는가 — 설계-체크리스트 8번 (배포 관련 커밋이면 실측 필수)
3. 기능 커밋이면 그 기능의 `qa/test-<기능>.sh` 가 로컬에서 통과했는가
4. 코드와 문서를 같은 커밋에 — 스키마를 바꾸면 테이블설계.md(+노션), 새 결정은 ADR
5. **새 `V` 파일이면** — 이미 적용된 V 파일은 절대 고치지 않았는가(ADR-045) / 옛 jar 로 롤백해도 도는가(ADR-053) / 바꾸는 테이블의 행 수를 로컬·운영에서 확인했는가

**push 전 (코드가 들어간 push)**
1. **DBeaver 의 EC2 연결을 끊는다** — 목록에서 `ledger_db(EC2)` 아이콘에 초록 체크가 없으면 끊긴 것. 열린 트랜잭션이 Flyway 의 `ALTER` 를 멈춰 새 앱이 안 뜬다(위 DBeaver "주의")
2. **CI 가 끝날 때까지 운영 DB 를 조회하지 않는다** — 수동 커밋이라 SELECT 한 번이 트랜잭션을 연다
3. 새 `V` 파일이 들어가면 — 오늘 백업이 성공했는가 (Healthchecks.io `ledger-backup` up)

**push 후**
1. GitHub Actions — `build` · `qa` · `deploy` 셋 다 초록. `qa` 는 원본 로그에서 케이스 수가 줄지 않았는지 (2026-09-29 기준 223 — `test-refresh.sh` 31 추가)
2. `https://dotoree.app/actuator/health` 가 UP (UptimeRobot DOWN 메일이 없는가)
3. `V` 파일이 있었으면 — 운영 `flyway_schema_history` 에 새 버전 `success = 1` (위 "운영 DB 를 만질 때" 대로 조회 → Rollback → Disconnect)
4. 바꾼 기능을 운영 화면에서 한 번 눌러 본다

**잘못됐을 때**
- `qa` 가 빨강 → 배포되지 않는다. 운영은 그대로이니 로컬에서 고쳐 다시 push
- `deploy` 가 빨강이거나 health 가 DOWN → `systemctl status ledger`, `journalctl -u ledger -n 50` (위 "앱 서비스 조작")
- 앱이 Flyway 단계에서 멈춘 것 같으면 → 열린 DB 연결부터 의심. DBeaver EC2 를 Disconnect 하면 기다리던 `ALTER` 가 이어서 진행된다

### 인스턴스 유형 변경 (small ↔ medium)

1. 인스턴스 선택 → 인스턴스 상태 → **인스턴스 중지** ("종료(삭제)" 가 아님 — 종료는 디스크까지 지운다)
2. `중지됨` 이 되면 작업 → 인스턴스 설정 → **인스턴스 유형 변경** → `t4g.medium` → 변경
3. 인스턴스 상태 → **인스턴스 시작**
4. 확인 — 유형이 바뀌었는가 / 퍼블릭 IP 가 그대로인가 / 작업 → 인스턴스 설정 → 크레딧 사양 변경 에서 "무제한 모드" 가 **체크 해제**인가

운영 중엔 1~3 사이 몇 분간 서비스가 내려간다. systemd 자동 기동(2단계)이 돼 있으면 시작 후 앱은 알아서 올라온다.

### 앱 서비스 조작 (`ledger.service`)

```
sudo systemctl start ledger      # 지금 띄우기
sudo systemctl stop ledger
sudo systemctl restart ledger    # 배포 후
systemctl status ledger          # 상태 한 눈에 (active / 마지막 로그 몇 줄)
journalctl -u ledger -f          # 로그 실시간 (Ctrl+C 로 빠져나옴)
journalctl -u ledger -n 100      # 최근 100줄
journalctl -u ledger --since "10 min ago"
```

- 유닛 파일이나 `ledger.env` 를 고쳤으면 **`daemon-reload` 먼저**, 그다음 `restart`
- `enable` 은 "부팅 때마다", `start` 는 "지금". 별개다
- 기동 실패는 `Restart=always` 때문에 5초마다 반복된다 — `status` 에 재시작 횟수가 쌓이면 로그부터 본다

### Nginx · 인증서

```
sudo nginx -t && sudo systemctl reload nginx   # 설정 고친 뒤 — 검사 통과해야만 reload
sudo cat /etc/nginx/sites-available/ledger      # 지금 설정
sudo certbot certificates                       # 인증서·만료일
sudo certbot renew --dry-run                    # 갱신 리허설 (설정을 손으로 고친 뒤 꼭)
systemctl list-timers | grep certbot            # 자동 갱신 타이머
```

- 설정 파일을 채팅·문서에서 복사해 붙였다면 `grep -c '](' /etc/nginx/sites-available/ledger` 가 0 인지 (링크 오염 검사, 트러블슈팅 16)
- `restart` 가 아니라 `reload` — 연결을 끊지 않는다
- 인증서가 만료되면 `.app` 은 HSTS preload 라 **브라우저로 아예 안 열린다**. 타이머가 실패했는지부터 `journalctl -u certbot`

### DB 백업 · 복구

```
systemctl list-timers ledger-backup.timer     # 다음 실행(매일 04:00)
journalctl -u ledger-backup -n 20             # 최근 결과 — backup ok / backup FAILED: …
sudo ls -l /var/backups/ledger/               # 로컬 사본(약 7일)
sudo systemctl start ledger-backup.service    # 지금 한 번 (systemd 경유 — 타이머와 같은 환경)
```

- S3 쪽은 **서버에서 볼 수 없다**(역할에 목록·읽기 권한이 없음 — 의도). 콘솔 → 버킷 → `daily/` 에서 루트로 확인
- 스크립트를 고칠 땐 저장소 `ops/` 를 고치고 서버에 같은 내용으로 설치 → `sudo bash -n /usr/local/sbin/ledger-backup` → `systemctl start` 로 1회
  ```
  # 맥 (저장소 폴더)
  scp -i ~/.ssh/ledger-admin.pem ops/<파일…> ubuntu@<EC2_HOST>:/tmp/
  # 서버 — 스크립트 700, 유닛 644
  sudo install -o root -g root -m 700 /tmp/ledger-backup /usr/local/sbin/ledger-backup && rm /tmp/ledger-backup
  sudo sha256sum /usr/local/sbin/ledger-backup | cut -c1-12      # 맥: shasum -a 256 < ops/ledger-backup | cut -c1-12 와 같아야 한다
  ```
  `sudo sha256sum < 파일` 은 안 된다 — 리다이렉트는 sudo 밖에서 열린다(트러블슈팅 21)

**복구 — 서버가 살아 있을 때 (로컬 사본)**

```
sudo systemctl stop ledger
sudo /usr/local/sbin/ledger-backup                        # 망가진 지금 상태도 한 벌 떠 둔다(원인 조사용)
sudo mysql -e "DROP DATABASE ledger_db; CREATE DATABASE ledger_db"
sudo zcat /var/backups/ledger/<파일>.sql.gz | sudo mysql ledger_db
sudo systemctl start ledger
```

- `ledger_app` 의 `ledger_db.*` 권한은 DB 를 지웠다 만들어도 남는다(권한은 `mysql.db` 에 있다). `flyway_schema_history` 도 덤프에 들어 있어 앱이 그대로 뜬다
- 덤프는 `--databases` 없이 떠서 `CREATE DATABASE`·`USE` 가 없다 → **아무 이름의 DB 에나 부을 수 있다**(리허설은 `ledger_restore_test`)

**복구 — 서버가 없을 때 (S3)**

1. 루트 콘솔 → S3 → 버킷 → `daily/` → 파일 체크 → 다운로드
2. **Safari 는 받자마자 `.gz` 를 풀어 `.sql` 로 저장한다**(트러블슈팅 19). `ls` 로 확장자부터 보고
   - `.sql` 이면 `tail -1` 이 `-- Dump completed on …` 인지 확인 → `mysql … < 파일.sql`
   - `.sql.gz` 면 `gzip -t` → `gunzip -c 파일 | mysql …`
3. 새 서버에 `scp` 로 올려 위 절차. 받은 덤프엔 이메일·비번 해시가 있다 — **끝나면 지운다**
- 누가 덮어썼다면 — `daily/` 에서 **버전 표시** 를 켜고 이전 버전을 받는다(이전 버전은 7일 보관)
- 리허설 2026-09-22 — S3 → 맥 `ledger_restore_test`, 19테이블 `COUNT(*)` 운영과 일치 (진행상황.md 5-5 의 쿼리)

### 감시 (UptimeRobot · Healthchecks.io)

```
systemctl list-timers ledger-backup.timer ledger-certcheck.timer   # 다음 실행 04:00 · 09:00
journalctl -u ledger-certcheck -n 5 -o cat                          # cert ok: N days left / cert FAILED: …
sudo systemctl start ledger-certcheck.service                       # 지금 한 번
```

- **알림 메일이 오면** — `DOWN | ledger-health` → 앱·Nginx(`systemctl status ledger nginx`, `journalctl -u ledger -n 50`) / `DOWN | ledger-backup` → `journalctl -u ledger-backup -n 20` / `DOWN | ledger-cert` → `sudo certbot renew --dry-run` · `journalctl -u certbot` · 갱신됐는데 옛 인증서면 `sudo systemctl reload nginx`
- **서버를 오래 끌 때** — UptimeRobot 모니터 Pause + Healthchecks 체크 Pause. 안 하면 DOWN 메일이 온다
- 핑 URL 을 바꿔야 하면(유출·계정 재생성) — Healthchecks 에서 체크를 새로 만들고 서버에서:
  ```
  sudo sed -i '/^HC_BACKUP_URL=/d' /etc/ledger/backup.env
  read -rsp 'URL: ' U; echo; [[ "$U" =~ ^https://hc-ping\.com/[0-9a-f-]{36}$ ]] && printf 'HC_BACKUP_URL=%s\n' "$U" | sudo tee -a /etc/ledger/backup.env >/dev/null; unset U
  ```
  명령 안에 URL 을 쓰지 않는다 — 프롬프트가 뜬 뒤 붙여넣는다(셸 기록에 안 남게)
- 실패 알림 실측(2026-09-22) — 덤프 앞에 `false &&` 를 끼운 `/tmp` 사본을 실행 → DOWN 메일 → 진짜 백업 → UP 메일. 절차는 진행상황.md 5-6

### 요금 확인

과금 정보 및 비용 관리 → 청구서(이번 달 누적) / Cost Explorer(일별). 예산 알림 메일이 오면 먼저 **EC2 대시보드에서 모르는 인스턴스가 떠 있는지**, 리전을 바꿔가며 확인.

### 전부 지울 때 (프로젝트 종료)

> **2026-09-30 결정 — 취업하면 서비스 종료.** 인프라·배포·CI/CD 만 걷어 과금을 끊고, **코드와 저장소는 그대로 둔다**(나중에 재활용). AI 에게 위임해 이 순서대로 진행 — 되돌릴 수 없는 삭제라 **단계마다 확인받고** 실행. Cloudflare 는 봇 검사로 AI 브라우저 로그인이 안 돼 본인이 직접.

순서는 "데이터 챙기기 → 들어오는 문 닫기 → 돈 나가는 것 → 나머지" (과금은 EC2·EIP·디스크가 거의 전부)

1. **챙기기** — 마지막 DB 백업을 S3 에서 맥으로 내려받아 저장소 밖 개인 보관처에(재활용 때 스키마·시연 데이터 참고용). 문의 메일함에 남은 메일 확인
2. **사용자에게 알리기** — 사용자가 있으면 처리방침 "종료 시 파기" 대로 공지 → 종료일에 운영 DB 파기(인스턴스 종료로 디스크째 삭제)
3. **CI/CD 멈추기 (GitHub)** — `ci.yml` 의 deploy 잡 제거 또는 Actions 비활성화 → Secrets 삭제(EC2 호스트·배포 키·역할 ARN 등, 목록은 아래 "비밀 · 식별자 목록표"). 저장소는 공개 그대로(포트폴리오)
4. **EC2** — `ledger-prod` 종료 → **탄력적 IP 릴리스**(빼먹으면 매달 과금) → 보안 그룹 `launch-wizard-1` · 키 페어 삭제. 디스크(30GB)는 종료 시 같이 삭제되는지 확인
5. **S3** — 백업 버킷 · 문의 메일함 버킷 비우고 삭제(버전 관리 켜진 백업 버킷은 이전 버전까지)
6. **SES · SNS** — 수신 규칙 세트 `ledger-inbound` 비활성 → 삭제, 도메인 자격 증명 `dotoree.app` 삭제, SNS 주제 `ledger-mail-notify` 삭제
7. **IAM** — 역할 `ledger-github-deploy` · `ledger-prod-ec2`, 정책 `ledger-deploy-sg-ssh` · `ledger-backup-s3-put`, OIDC 제공업체 삭제. `ledger-admin`(사람) 계정은 마지막에 판단
8. **Cloudflare (본인)** — DNS 레코드 정리, 도메인 `dotoree.app` **자동 갱신 끄기**(다음 갱신 2027-09 전에). 도메인을 살려 둘지는 그때 결정
9. **외부 감시** — UptimeRobot · Healthchecks.io 모니터 삭제(안 지우면 DOWN 메일이 계속 온다)
10. **확인** — 다음 달 청구서 $0 확인 뒤 예산 알림 `ledger-monthly` 삭제. 이 문서 "작업 이력" 에 한 줄

## 키 파일 보관

- `.pem` 은 **iCloud·드롭박스로 동기화되는 폴더를 피한다** (맥의 "데스크탑 및 문서 폴더" 동기화가 켜져 있으면 `~/Documents` 도 올라간다). 표준 위치는 `~/.ssh/`, 권한 `400`
- 저장소 폴더 안에 두지 않는다. 실제 위치는 저장소 밖 개인 보관처에만 적어 둔다
- 첫 접속 때 나오는 `ED25519 key fingerprint ... (yes/no)` 는 "이 서버를 처음 본다"는 확인. `yes` 하면 `~/.ssh/known_hosts` 에 기록되고 다음부터 안 묻는다

## 계정 보안

- 루트 계정 **MFA**(로그인 때 비밀번호 + 휴대폰 앱의 6자리 코드) — 루트는 결제 수단에 무제한 권한이라, 털리면 비싼 인스턴스 수십 대로 수백만원이 청구되는 사고가 흔하다
  1. 휴대폰에 Google Authenticator 설치 (설정에서 Google 계정 백업 켜기 — 폰 분실 대비)
  2. 콘솔 오른쪽 위 계정 이름 → **보안 자격 증명** → **MFA 디바이스 할당**
  3. 디바이스 이름(영문·숫자·`-`) → **인증 관리자 앱** → **QR 코드 표시** → 앱의 `+` 로 스캔
  4. **MFA 코드 1** 에 지금 6자리, 약 30초 뒤 숫자가 바뀌면 **MFA 코드 2** 에 새 6자리 (같은 숫자 두 번은 실패) → **MFA 추가**
  5. 확인 — 로그아웃 후 재로그인 때 코드를 묻는가
  - **패스키 대신 OTP 를 고른 이유** (2026-09-17 실측) — 패스키는 등록한 브라우저(Safari·Chrome)의 저장소에 묶인다. Claude 내장 브라우저에선 "저장된 패스키가 없습니다"만 나오고 Touch ID 가 안 뜬다. OTP 는 숫자 입력이라 어느 브라우저에서든 된다
  - MFA 를 지우거나 바꾸려면 **MFA 로 인증하고 들어온 세션**이어야 한다 — 등록 전에 로그인해 둔 세션에선 "please ensure that you are authenticated with an MFA device" 로 거부된다. 로그아웃 → 재로그인
  - `MFA device already exists` 인데 목록은 (0) — QR 을 띄우는 순간 그 이름으로 기기가 먼저 만들어지고, 코드 2개를 넣어야 할당된다. 중간에 멈추면 안 보이는 미할당 기기가 남아 같은 이름이 충돌한다. **이름을 바꿔 다시** + 폰 앱의 옛 항목은 지우고 새 QR 을 스캔. 남은 미할당 기기는 무해(과금 없음). 지우려면 CloudShell 에서 `aws iam list-virtual-mfa-devices --assignment-status Unassigned` → `aws iam delete-virtual-mfa-device --serial-number <값>`
  - "코드가 유효하지 않음" 이 반복되면 휴대폰 시계를 자동 설정으로. 휴대폰을 바꾸기 전에 새 기기로 먼저 옮길 것
- 루트 계정에 **액세스 키를 만들지 않는다**. CD 나 백업에 AWS 권한이 필요해지면 IAM 역할 · 최소 권한 사용자로 (5단계)

## 비밀 · 식별자 목록표 (2026-09-19)

**값은 없다 — 이름, 어디에 있는지, 무엇을 여는지, 유출되면 무엇을 갈아 끼우는지.** 분류 기준은 CLAUDE.md "값 3단 분류".
사고가 난 뒤엔 침착하게 찾아볼 여유가 없어서, 교체 절차를 미리 적어 둔다.

| 이름 | 분류 | 있는 곳 | 무엇을 여는가 | 유출·분실 시 |
|---|---|---|---|---|
| AWS 루트 비밀번호 + MFA | 비밀 | 암호 관리처 + 폰 OTP | 계정 전체(결제 포함) | 비번 변경 → MFA 재등록 → 모르는 인스턴스·IAM 주체 확인 |
| `ledger-admin` 개인키(pem) | 비밀 | 맥 `~/.ssh/` | `ubuntu` — sudo 전권 | **재발급 불가.** 새 키쌍 생성 → `ubuntu` 의 `authorized_keys` 교체 → AWS 키 페어 삭제 |
| `ledger-deploy` 개인키 | 비밀 | 맥 `~/.ssh/` + GitHub Secret `EC2_SSH_KEY` | `deploy` — jar 교체 + `restart` 만 | `ssh-keygen` 새로 → `deploy` 의 `authorized_keys` 교체 → Secret 갱신. 내 접속은 안 끊긴다 |
| `DB_PASSWORD` (운영) | 비밀 | EC2 `/etc/ledger/ledger.env` + 개인 보관처 | `ledger_db` 전체 | `ALTER USER 'ledger_app'@'localhost' IDENTIFIED BY ...` → `ledger.env` → `restart` |
| `JWT_SECRET` (운영) | 비밀 | EC2 `ledger.env` | 로그인 토큰 서명(위조 가능해짐) | `openssl rand -base64 48` 새로 → `ledger.env` → `restart`. **전원 재로그인** |
| `AWS_ROLE_ARN` · `AWS_SG_ID` · `EC2_HOST` | 식별자 | GitHub Secrets + 개인 보관처 | 단독으론 아무것도 못 연다 | 교체 불필요. 공개 로그에 안 찍히게 Secret 에 둔 것 |
| `EC2_HOST_KEY` (서버 호스트 공개키) | 식별자 | GitHub Secret. 원본은 서버 `/etc/ssh/ssh_host_ed25519_key.pub`, 사본은 맥 `~/.ssh/known_hosts` | 아무것도 못 연다 — CD 가 "진짜 그 서버인가"를 확인하는 지문 | 따로 보관할 필요 없음(언제든 다시 꺼낼 수 있다). **서버를 새로 만들면 값이 바뀌므로 Secret 갱신** |
| `AWS_REGION` · `EC2_USER` | 공개 설정 | `ci.yml` 에 그대로 | — | — |
| CI 일회용 값(`ci_test_password` 등) | 공개 | `ci.yml` | 러너 안 일회용 DB | 해당 없음. **운영 값은 반드시 다르게** |
| `S3_BACKUP_BUCKET` | 식별자 | EC2 `/etc/ledger/backup.env`(root 600) + 개인 보관처 | 단독으론 아무것도 못 연다. 서버 역할도 이 버킷엔 올리기만 | 교체 불필요. 버킷을 새로 만들면 파일 한 줄 갱신 — 손으로 쓰지 말고 조립(트러블슈팅 18) |
| `HC_BACKUP_URL` · `HC_CERT_URL` (Healthchecks 핑 URL) | **약한 비밀** | EC2 `/etc/ledger/backup.env`(root 600) + Healthchecks 대시보드(정본). 개인 보관처엔 두지 않는다 | 알림만 — 아는 사람이 "성공" 핑을 보내 멈춘 백업·인증서 알림을 끌 수 있다. 백업·S3 는 못 건드린다 | Healthchecks 에서 체크를 새로 만들어 URL 교체 → `backup.env` 한 줄 교체(위 "감시") → 옛 체크 삭제 |
| UptimeRobot · Healthchecks.io 계정 | 비밀 | 매직 링크(= 메일함) + 2FA TOTP(폰). Healthchecks 는 복구 코드가 없어 QR 캡처를 개인 보관처에 | 모니터·체크를 지워 알림을 끌 수 있다 | 2FA 분실 시 — UptimeRobot 은 모니터 1개 재생성 / Healthchecks 는 새 계정 → 체크 2개 → `backup.env` 두 줄 교체(약 15분) |
| `QA_PASSWORD` | 테스트 | 맥 셸 환경변수 | 로컬 테스트 계정 | — (운영 `test@test.com` 은 공개 전 정리 — 로드맵) |

- 역할 `ledger-github-deploy` 에는 **보관할 비밀이 없다** — OIDC 라 실행마다 1시간짜리 임시 자격을 받는다. `ledger-prod-ec2` 도 같다 — 인스턴스 메타데이터(IMDSv2)에서 임시 자격을 받는다
- IAM 사용자는 0명으로 유지한다. 사용자를 만들면 장기 액세스 키가 생길 자리가 생긴다 — AWS 권한이 필요하면 역할로

## 작업 이력

| 날짜 | 작업 | 누가 |
|---|---|---|
| 2026-09-17 | `ledger-prod` 생성(t4g.medium) · 키 페어 · 보안 그룹 · 탄력적 IP 연결 · 크레딧 Standard | AI 가 콘솔 조작, 과금 클릭 전 사용자 확인 |
| 2026-09-17 | t4g.medium → t4g.small (중지 → 변경 → 시작, 5분) | AI 가 콘솔 조작 |
| 2026-09-17 | 예산 알림 `ledger-monthly` $30 | AI 가 콘솔 조작 |
| 2026-09-17 | pem 권한 설정 · SSH 첫 접속 확인 | 직접 |
| 2026-09-17 | 루트 MFA — 폰 OTP(Google Authenticator). 패스키(Touch ID)로 등록했다가 제거 | 직접 |
| 2026-09-17 | OS 타임존 KST → `apt upgrade`(169개) → 커널 재부팅 → MySQL 8.4.11 설치 → `mysql_secure_installation` | 직접 |
| 2026-09-17 | `ledger_db` 생성 · 앱 계정 `ledger_app` 생성 · `ledger_db.*` 권한 부여 (ADR-043 · ADR-044) | 직접 |
| 2026-09-17 | Java 17 JRE(headless) 설치 · 앱 실행 유저 `ledger` 생성(시스템 계정 · `nologin` · 비번 없음) | 직접 |
| 2026-09-18 | `/opt/ledger`·`/etc/ledger` 생성 · `ledger.env`(root 600, 값 4개) · `ledger.service` 작성 · `daemon-reload`·`enable` | 직접 |
| 2026-09-18 | 2-5 수동 첫 배포 — jar `scp` → `systemctl start` → Flyway V1 적용(19테이블). 테이블명 대소문자로 1회 실패 후 DB 재생성(ADR-045) · 가입·로그인·거래 확인 · 타임존 3계층 확인 · 재부팅 자동 기동 확인 | 직접 |
| 2026-09-18 | DBeaver 운영 DB 커넥션 — SSH 터널(pem) + `ledger_app`. 3306 은 열지 않음 | 직접 |
| 2026-09-18 | 3단계 CD 준비 — 배포 키쌍 `ledger-deploy` 생성 · `deploy` 계정 + `authorized_keys` · `/opt/ledger` 소유권 이전 · sudoers 한 줄(`restart` 만) · 권한 경계 실측(stop·`ledger.env` 거부) | 직접 |
| 2026-09-19 | 3-4 CD 권한(B안) — IAM **OIDC 공급자** 등록. 배포할 때만 보안 그룹 22번에 러너 IP 를 열고 닫기 위한 권한 준비 | AI 가 콘솔 조작(내장 브라우저) |
| 2026-09-19 | IAM 정책 `ledger-deploy-sg-ssh` 생성(시각적 편집기, EC2 2작업 + SG ARN 1개) | AI 가 콘솔 조작(내장 브라우저) |
| 2026-09-19 | IAM 역할 `ledger-github-deploy` 생성 — 웹 자격 증명(OIDC) · main 브랜치 한정 · 정책 1개 연결 | AI 가 콘솔 조작(내장 브라우저) |
| 2026-09-20 | `ledger-github-deploy` 신뢰 정책 수정 — `sub` 에 숫자 ID 형태 추가(3-6 실패 원인) | AI 가 콘솔 조작(내장 브라우저) |
| 2026-09-21 | Cloudflare 가입 · 2FA(TOTP) → `dotoree.app` 구매(1년, 자동 갱신) → DNS `A @` · `CNAME www`(DNS only). `dig +short` 로 두 이름 모두 탄력적 IP 확인 | 직접 (이름 후보 가용성·겹침 검색은 AI) |
| 2026-09-21 | Nginx 설치 · 설정(80 문지기 · 앱 프록시) → certbot 인증서 발급(`--dry-run` 먼저) · 80→443 → 설정 재작성(443 문지기 · `/actuator` 404) · `renew --dry-run` 재확인 | 직접 (AI 안내) |
| 2026-09-22 | DB 백업 로컬판 — 스크립트 `ledger-backup`(root·`auth_socket`) · 수동 실행(19테이블 · `Dump completed`) · 완료 검사 추가 후 **잘린 덤프 흉내로 차단 실측**(`gzip -t` 는 통과, 완료 검사가 잡음) · systemd 타이머 `enable --now`(NEXT 04:00) · `systemctl start` 로 1회 성공 | 직접 (AI 안내) |
| 2026-09-22 | S3 백업 버킷 생성(계정 리전 네임스페이스 · 버전 관리 · 퍼블릭 차단) → 수명 주기 `daily-expire` → IAM 정책 `ledger-backup-s3-put` → 역할 `ledger-prod-ec2` → `ledger-prod` 에 연결 · IMDSv2 Required 확인 | 직접 (AI 안내, Safari 화면으로 저장 전 확인) |
| 2026-09-22 | AWS CLI(snap) · 역할로 인증 확인(`assumed-role/ledger-prod-ec2`, 키 파일 없음) → `backup.env` → 스크립트에 S3 업로드 추가 · systemd 로 1회 성공 → **권한 실측 6종**(목록·읽기·삭제·`daily/` 밖 쓰기 거부 / `daily/` 쓰기·덮어쓰기 허용 → 콘솔 "버전 표시"로 이전 버전 보존 확인) → **복구 리허설**(S3 → 맥, 19테이블 행 수 일치) | 직접 (AI 안내) |
| 2026-09-22 | 5-6 감시 — UptimeRobot 가입·2FA → 키워드 모니터 `ledger-health`(HTTP 모니터에서 교체, SSL·heartbeat·상태 코드는 유료라 확인 후 포기) · Healthchecks.io 가입·2FA·시간대 → 체크 `ledger-backup`·`ledger-cert`(1일 + 1시간) · Gmail 필터 2개 | 직접 (AI 안내, 필터·시간대는 AI 가 내장 브라우저로) |
| 2026-09-22 | `backup.env` 에 `HC_BACKUP_URL`·`HC_CERT_URL`(`read -s` → 형식 검사) → `ops/` 스크립트 설치(`ledger-backup` 교체 + `ledger-certcheck`·유닛 2개, sha256 일치) → `enable --now ledger-certcheck.timer` → 1회 실행 두 체크 up → **실패 흉내로 DOWN·UP 메일 실측** | 직접 (AI 안내) |
| 2026-09-22 | SES 도메인 자격 증명 `dotoree.app`(Easy DKIM 2048, Route 53 게시 끔) → Cloudflare 에 DKIM CNAME 3개 + `_dmarc` TXT → 공개 DNS 조회로 값·DNS only·이중 도메인 없음 확인 | SES 는 AI 가 콘솔 조작(내장 브라우저) / Cloudflare 는 직접(내장 브라우저가 Turnstile 에 막힘) |
| 2026-09-23 | Cloudflare Email Routing — `support@dotoree.app` → Gmail(목적지 인증 · 라우팅 규칙). 공개 DNS 로 MX·SPF·DKIM 추가 + SES DKIM·DMARC 유지 확인 | 직접 (AI 가 메뉴 위치 안내 · DNS 검사) |
| 2026-09-23 | EC2 `/etc/nginx/sites-available/ledger.bak-certbot` 삭제 → `nginx -t` ok | 직접 |
| 2026-09-23 | 운영 DB 확인용 계정 `test@test.com`(2-5 에서 만든 것) 하드 삭제 — 거래·카테고리·계정. 다른 계정에서 support@ 로 보낸 메일 Gmail 수신 확인 | 직접 (DBeaver `ledger_db(EC2)`) |
| 2026-09-25 | DBeaver `ledger_db(EC2)` 안전 장치 기록 — 읽기 전용 + 수동 커밋(설정은 이전 세션) · Production(빨간 탭) 반영 확인. `SELECT COUNT(*) FROM refresh_tokens` 뒤 열린 트랜잭션을 Rollback → 카운터 `None` 확인 | 직접 (확인은 AI 가 DBeaver 화면으로) |
| 2026-09-25 | V3 배포 확인 — CI #63 build·qa·deploy 초록(qa 192/192) · health UP · `flyway_schema_history` V3 `success 1`(15:25:50) · `SHOW CREATE TABLE refresh_tokens` 로컬과 일치 → Rollback · Disconnect | AI (내장 브라우저로 Actions 로그 · DBeaver 화면 조작, 사용자 허락) |
| 2026-09-30 | 문의 메일 국내 수신 1~2단계 — S3 `<S3_MAIL_BUCKET>` 생성(계정 리전 네임스페이스 · 버전 끔) → 수명 주기 `inbox-expire`(inbox/ 90일 · 멀티파트 7일) → 버킷 정책(SES 만 PutObject, SourceAccount·SourceArn) → SES 수신 규칙 세트 `ledger-inbound` + 규칙 `support-to-s3`(support@ → S3 inbox/, 스팸 검사) → 활성으로 설정 → `inbox/AMAZON_SES_SETUP_NOTIFICATION` 생성 확인. 겪은 것 — 첫 규칙 생성이 `Could not write to bucket`: 콘솔 코드 편집기에 스크립트로 넣은 정책이 **빈 정책으로 저장**돼 있었다(편집기 붙여넣기로 다시 저장하니 통과). 정책 검사기 지적 `StringLike`→`ArnLike` 반영 | AI (내장 브라우저, 본인 요청 위임 — 단계마다 값 확인 뒤 실행) |
| 2026-09-30 | 3~4단계 — Cloudflare Email Routing 끄기(도메인 온보딩 해제 + Cloudflare DNS 레코드 삭제) → `MX @ 10 inbound-smtp.ap-northeast-2.amazonaws.com` 추가 → 공개 DNS 로 옛 MX 3줄 사라짐 확인(새 MX 는 "없음" 응답이 SOA 최소 1800초 캐시돼 늦게 보임) → 개인 메일로 `support@` 테스트 → S3 `inbox/` 에 3.9KB 객체 도착(3분 안), 헤더 `Received: inbound-smtp.ap-northeast-2` · `X-SES-Spam-Verdict: PASS` · `X-SES-Virus-Verdict: PASS` | Cloudflare 는 직접(AI 가 Safari 화면·DNS 로 확인) / S3 확인은 AI |
| 2026-09-30 | 5단계 새 메일 알림 — SNS 주제 `ledger-mail-notify`(표준, 정책에 S3 게시 문 추가) → 이메일 구독 생성(확인 대기) → S3 이벤트 알림 `inbox-new-mail`(inbox/ · Put → 주제). 겪은 것 — 콘솔 드롭다운 선택이 스크립트 클릭으론 반영 안 돼 "SNS 주제를 선택해야 합니다" → "ARN 입력" 방식으로 저장 | AI (내장 브라우저, 본인 요청 위임) |
| 2026-09-30 | 저장소 이름 `ledger` → `dotoree` — `ledger-github-deploy` 신뢰 정책에 `dotoree` 두 형태 추가(본인) → GitHub Rename(AI) → 맥 remote(AI) → CI #72 Re-run build·qa·deploy 초록 → 옛 `ledger` 두 줄 삭제(본인) → 2줄만 남은 것 확인. 신뢰 정책 편집은 AI 쪽에서 막혀 본인이 직접 | 본인 + AI (내장 브라우저) |
