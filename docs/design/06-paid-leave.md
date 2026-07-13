# 有給休暇申請機能 — 設計

要求仕様: `docs/requirements/02-paid-leave.md`

---

## 1. ドメイン分析

### 新規ドメイン

| ドメイン | 責務 | 主な Entity |
|---------|------|------------|
| leave | 有給休暇の付与・申請・承認・残日数管理 | LeaveGrant, LeaveRequest |

### 既存ドメインへの影響

| ドメイン | 変更内容 |
|---------|---------|
| report | 月次集計に有給取得日数・残日数を追加。欠勤日数から有給取得日を除外 |
| attendance | 勤務時間計算で半日/時間単位休暇時の残業判定基準を変更 |

---

## 2. Entity 定義

### LeaveGrant（有給付与記録）

年度ごとの付与・消化・失効を管理する。

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | |
| employee | Employee | FK, NOT NULL | 付与対象の社員 |
| fiscalYear | int | NOT NULL | 年度（4月始まり。例: 2026 = 2026年4月〜2027年3月） |
| grantDate | LocalDate | NOT NULL | 付与日 |
| grantedDays | BigDecimal | NOT NULL | 付与日数（整数だが半日消化の計算用に Decimal） |
| usedDays | BigDecimal | NOT NULL, default 0 | 消化済み日数（全日=1.0, 半日=0.5, 時間=hours/8） |
| expiryDate | LocalDate | NOT NULL | 失効日（付与日の2年後の年度末） |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | |
| updatedAt | Instant | NOT NULL | |

ビジネスルール:
- 付与は入社日起算で計算（入社6ヶ月後に初回、以降1年ごと）
- 年度 = 4月始まり（4/1〜翌3/31）
- 2年時効: `expiryDate` を過ぎた付与は消化不可
- 消化順序: `expiryDate` が早いもの（= 古い付与）から先に消化

### LeaveRequest（有給休暇申請）

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | |
| requester | Employee | FK, NOT NULL | 申請者 |
| approver | Employee | FK, nullable | 承認/却下した上長。処理前は null |
| targetDate | LocalDate | NOT NULL | 休暇取得日 |
| leaveType | LeaveType (enum) | NOT NULL | FULL_DAY / HALF_DAY / HOURLY |
| hours | Integer | nullable | 時間単位の場合の取得時間（1〜7）。FULL_DAY/HALF_DAY は null |
| reason | String | NOT NULL | 申請理由 |
| status | LeaveRequestStatus (enum) | NOT NULL | PENDING / APPROVED / REJECTED / WITHDRAWN |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | |
| updatedAt | Instant | NOT NULL | |

ビジネスルール:
- 1申請 = 1日分。同日に全日+半日の重複申請は不可
- 残日数不足の場合は申請エラー
- 時間単位年休の年間上限チェック（年5日分=40時間）
- 承認フローは勤怠修正と同じ（所属部署の上長が承認、上長は自己承認）
- 取り下げ（WITHDRAWN）は承認前・承認後いずれも可能。消化済み日数が戻る
- 承認操作と取り下げの競合は楽観ロックで制御

---

## 3. Enum 定義

### LeaveType（休暇単位）

```
FULL_DAY  — 全日（1.0日消化）
HALF_DAY  — 半日（0.5日消化）
HOURLY    — 時間単位（hours/8 日消化）
```

### LeaveRequestStatus（申請ステータス）

```
PENDING    — 申請中（上長の承認待ち）
APPROVED   — 承認済み
REJECTED   — 却下
WITHDRAWN  — 取り下げ済み（申請者によるキャンセル）
```

---

## 4. Value Object

### LeaveBalance（有給残日数）

計算結果を返す Value Object。DB には保持しない。

| フィールド | 型 | 説明 |
|-----------|-----|------|
| totalGrantedDays | BigDecimal | 今年度の総付与日数（当年度分＋繰越分） |
| usedDays | BigDecimal | 消化済み日数 |
| remainingDays | BigDecimal | 残日数 = totalGrantedDays - usedDays |
| hourlyUsedHours | int | 時間単位年休の当年度使用時間 |
| hourlyRemainingHours | int | 時間単位年休の残り時間（上限40h - 使用済み） |
| currentYearGrantDays | BigDecimal | 当年度付与分 |
| carriedOverDays | BigDecimal | 前年度繰越分 |

---

## 5. ドメイン関連図

```
┌──────────────────┐
│    Employee      │
└──────┬─────┬─────┘
       │1    │1
       │     │
    N  │     │  N
┌──────▼─────┐  ┌────────▼──────────┐
│ LeaveGrant │  │   LeaveRequest    │
├────────────┤  ├───────────────────┤
│ fiscalYear │  │ targetDate        │
│ grantDate  │  │ leaveType (enum)  │
│ grantedDays│  │ hours             │
│ usedDays   │  │ reason            │
│ expiryDate │  │ status (enum)     │
└────────────┘  │ approver → Employee│
                └───────────────────┘
```

関連:
- Employee `1 : N` LeaveGrant — 1社員に年度ごとの付与記録
- Employee `1 : N` LeaveRequest (requester) — 1社員が複数の有給申請
- Employee `1 : N` LeaveRequest (approver) — 1上長が複数の申請を承認

---

## 6. 付与ロジック

### 付与日数テーブル（労基法準拠）

| 勤続年数 | 付与日数 |
|----------|----------|
| 0.5 年 | 10 日 |
| 1.5 年 | 11 日 |
| 2.5 年 | 12 日 |
| 3.5 年 | 14 日 |
| 4.5 年 | 16 日 |
| 5.5 年 | 18 日 |
| 6.5 年以上 | 20 日 |

### 付与タイミング

1. 入社6ヶ月後に初回付与（10日）
2. 以降、前回付与日の1年後に次回付与
3. システムは日次バッチまたは残日数照会時にオンデマンドで付与レコードを生成

### 年度への読み替え

- 年度 = 4月始まり
- 付与レコードの `fiscalYear` は付与日が属する年度（4月〜翌3月）
- 例: 2026年10月1日入社 → 2027年4月1日に初回付与 → `fiscalYear = 2027`（2027年度）

### 失効日の計算

- `expiryDate` = 付与日が属する年度の翌々年度末
- 例: 2027年度付与 → 2029年3月31日に失効

---

## 7. 消化ロジック

### 消化量

| 休暇種別 | 消化日数 |
|----------|----------|
| 全日 | 1.0 日 |
| 半日 | 0.5 日 |
| 時間単位 (n時間) | n / 8 日 |

### 消化順序

古い付与（`expiryDate` が早いもの）から優先消化する:

1. 有効な付与レコードを `expiryDate` 昇順で取得
2. 消化量を先頭の付与から差し引く
3. 1つの付与で足りなければ次の付与から差し引く

### 取り下げ時の戻し

承認済み申請の取り下げ時:
1. `LeaveRequest.status` を `WITHDRAWN` に変更
2. 対応する `LeaveGrant.usedDays` から消化量を減算
3. 複数付与にまたがっていた場合は新しい方（`expiryDate` が遅い方）から戻す

---

## 8. DB 設計

### leave_grants（有給付与記録）

```sql
CREATE TABLE leave_grants (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    fiscal_year INTEGER NOT NULL,
    grant_date DATE NOT NULL,
    granted_days DECIMAL(4,1) NOT NULL,
    used_days DECIMAL(4,1) NOT NULL DEFAULT 0,
    expiry_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_leave_grants_employee ON leave_grants(employee_id);
CREATE INDEX idx_leave_grants_employee_fiscal ON leave_grants(employee_id, fiscal_year);
CREATE UNIQUE INDEX idx_leave_grants_employee_grant_date ON leave_grants(employee_id, grant_date);
```

### leave_requests（有給休暇申請）

```sql
CREATE TABLE leave_requests (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES employees(id),
    approver_id UUID REFERENCES employees(id),
    target_date DATE NOT NULL,
    leave_type VARCHAR(20) NOT NULL CHECK (leave_type IN ('FULL_DAY', 'HALF_DAY', 'HOURLY')),
    hours INTEGER,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_hours_for_hourly CHECK (
        (leave_type = 'HOURLY' AND hours BETWEEN 1 AND 7) OR
        (leave_type != 'HOURLY' AND hours IS NULL)
    )
);

CREATE INDEX idx_leave_requests_requester ON leave_requests(requester_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);
CREATE INDEX idx_leave_requests_target_date ON leave_requests(target_date);
```

### Flyway マイグレーション

| ファイル | 内容 |
|---------|------|
| `V6__create_leave_grants.sql` | leave_grants テーブル + インデックス |
| `V7__create_leave_requests.sql` | leave_requests テーブル + インデックス |

---

## 9. API 設計

### エンドポイント一覧

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/leaves/balance` | 自分の有給残日数 | 全ロール |
| POST | `/api/leaves/requests` | 有給申請 | 全ロール |
| GET | `/api/leaves/requests` | 自分の申請一覧 | 全ロール |
| PATCH | `/api/leaves/requests/{id}/withdraw` | 申請取り下げ | 全ロール |
| GET | `/api/leaves/requests/pending` | 承認待ち一覧（自部署） | 上長 |
| PATCH | `/api/leaves/requests/{id}/approve` | 承認 | 上長 |
| PATCH | `/api/leaves/requests/{id}/reject` | 却下 | 上長 |
| GET | `/api/leaves/summary` | 全社員の有給取得状況 | 管理者 |

---

### GET /api/leaves/balance

自分の有給残日数を取得する。未付与分がある場合はオンデマンドで付与レコードを生成する。

Response (200):
```json
{
  "fiscalYear": 2026,
  "totalGrantedDays": 21.0,
  "usedDays": 3.5,
  "remainingDays": 17.5,
  "hourlyUsedHours": 8,
  "hourlyRemainingHours": 32,
  "grants": [
    {
      "fiscalYear": 2025,
      "grantDate": "2025-10-01",
      "grantedDays": 11.0,
      "usedDays": 3.5,
      "remainingDays": 7.5,
      "expiryDate": "2027-03-31",
      "isCarriedOver": true
    },
    {
      "fiscalYear": 2026,
      "grantDate": "2026-10-01",
      "grantedDays": 10.0,
      "usedDays": 0.0,
      "remainingDays": 10.0,
      "expiryDate": "2028-03-31",
      "isCarriedOver": false
    }
  ]
}
```

---

### POST /api/leaves/requests

有給休暇を申請する。

Request:
```json
{
  "targetDate": "2026-07-20",
  "leaveType": "FULL_DAY",
  "hours": null,
  "reason": "私用のため"
}
```

Response (201):
```json
{
  "id": "019059d1-...",
  "targetDate": "2026-07-20",
  "leaveType": "FULL_DAY",
  "hours": null,
  "reason": "私用のため",
  "status": "PENDING",
  "createdAt": "2026-07-13T02:00:00Z"
}
```

Error:
- (400): バリデーションエラー（hours が範囲外等）
- (409): 残日数不足 / 時間単位年休の年間上限超過 / 同日に既に申請あり

---

### GET /api/leaves/requests

自分の有給申請一覧。

Query Parameters:
- `status` (任意): `PENDING` / `APPROVED` / `REJECTED` / `WITHDRAWN`
- `fiscalYear` (任意): 年度で絞り込み

Response (200):
```json
[
  {
    "id": "019059d1-...",
    "targetDate": "2026-07-20",
    "leaveType": "FULL_DAY",
    "hours": null,
    "reason": "私用のため",
    "status": "APPROVED",
    "approverName": "鈴木部長",
    "createdAt": "2026-07-13T02:00:00Z"
  }
]
```

---

### PATCH /api/leaves/requests/{id}/withdraw

申請を取り下げる。承認前・承認後いずれも可能。

Request: ボディなし

Response (200): 更新後の申請情報（`status: "WITHDRAWN"`）

Error: (409): 楽観ロックエラー / (404): 申請が見つからない / (400): 既に取り下げ済み・却下済み

---

### GET /api/leaves/requests/pending

自部署メンバーの承認待ち有給申請一覧。上長向け。

Response (200):
```json
[
  {
    "id": "019059d1-...",
    "requesterId": "019059a1-...",
    "requesterName": "田中太郎",
    "targetDate": "2026-07-20",
    "leaveType": "FULL_DAY",
    "hours": null,
    "reason": "私用のため",
    "status": "PENDING",
    "createdAt": "2026-07-13T02:00:00Z"
  }
]
```

---

### PATCH /api/leaves/requests/{id}/approve

有給申請を承認する。承認時に付与レコードの `usedDays` を加算する。

Request: ボディなし

Response (200): 更新後の申請情報（`status: "APPROVED"`, `approverName` が設定）

Error: (409): 楽観ロックエラー / 残日数不足（承認時点で再チェック）

---

### PATCH /api/leaves/requests/{id}/reject

有給申請を却下する。

Request:
```json
{ "reason": "業務上の都合により" }
```

Response (200): 更新後の申請情報（`status: "REJECTED"`）

Error: (409): 楽観ロックエラー

---

### GET /api/leaves/summary

全社員の有給取得状況。管理者向け。

Query Parameters:
- `fiscalYear` (任意, default: 当年度)
- `departmentId` (任意): 部署で絞り込み

Response (200):
```json
{
  "fiscalYear": 2026,
  "employees": [
    {
      "employeeId": "019059a1-...",
      "employeeName": "田中太郎",
      "departmentName": "開発部",
      "grantedDays": 20.0,
      "usedDays": 5.5,
      "remainingDays": 14.5,
      "hourlyUsedHours": 16
    }
  ]
}
```

---

## 10. 権限マトリクス（追加分）

| エンドポイント | 未認証 | 一般社員 | 上長 | 管理者 |
|--------------|--------|---------|------|--------|
| GET /api/leaves/balance | - | o | o | o |
| POST /api/leaves/requests | - | o | o | o |
| GET /api/leaves/requests | - | o | o | o |
| PATCH /api/leaves/requests/{id}/withdraw | - | o | o | o |
| GET /api/leaves/requests/pending | - | - | o | - |
| PATCH /api/leaves/requests/{id}/approve | - | - | o | - |
| PATCH /api/leaves/requests/{id}/reject | - | - | o | - |
| GET /api/leaves/summary | - | - | - | o |

---

## 11. 勤務時間計算への統合

### 既存の WorkDuration 計算への変更

半日休暇・時間単位休暇の日は残業判定基準を変更する:

| 休暇種別 | 所定労働時間 | 残業判定 |
|----------|------------|---------|
| なし（通常） | 8h | 8h超過分 |
| 半日休暇 | 4h | 4h超過分 |
| 時間単位 (n時間) | (8-n)h | (8-n)h超過分 |

WorkDuration の計算時に、当日の承認済み LeaveRequest を参照して判定基準を調整する。

### 月次集計への追加項目

| 項目 | 計算方法 |
|------|---------|
| 有給取得日数（当月） | 当月の APPROVED な LeaveRequest の消化日数合計 |
| 有給残日数 | LeaveBalance.remainingDays |
| 欠勤日数（修正） | 営業日 − 出勤日数 − 有給取得日数（全日） |

---

## 12. パッケージ構成（追加）

```
com.example.attendance
├── leave/              — 有給休暇（付与・申請・承認）
│   ├── controller/
│   │   └── LeaveController.java
│   ├── service/
│   │   ├── LeaveService.java (interface)
│   │   ├── LeaveServiceImpl.java
│   │   ├── LeaveGrantService.java (interface)
│   │   └── LeaveGrantServiceImpl.java
│   ├── repository/
│   │   ├── LeaveGrantRepository.java
│   │   └── LeaveRequestRepository.java
│   ├── entity/
│   │   ├── LeaveGrant.java
│   │   └── LeaveRequest.java
│   ├── domain/
│   │   ├── LeaveType.java (enum)
│   │   ├── LeaveRequestStatus.java (enum)
│   │   ├── LeaveBalance.java (VO)
│   │   └── LeaveGrantCalculator.java (付与日数計算)
│   └── dto/
│       ├── LeaveBalanceResponse.java
│       ├── LeaveRequestCreateRequest.java
│       ├── LeaveRequestResponse.java
│       ├── LeavePendingResponse.java
│       ├── LeaveRejectRequest.java
│       └── LeaveSummaryResponse.java
└── ...（既存ドメイン）
```

ドメイン間の依存:
- `leave` → `employee`（申請者・承認者の参照）
- `report` → `leave`（月次集計で有給取得日数を参照）
- `attendance` → `leave`（勤務時間計算で半日/時間休暇を参照）

---

## 13. 設計判断まとめ

| 項目 | 決定 | 根拠 |
|------|------|------|
| 残日数管理 | 付与レコード（LeaveGrant）の `usedDays` で管理。残高テーブルは持たない | 計算元が明確。付与×消化のトレーサビリティ確保 |
| 付与タイミング | 残日数照会時にオンデマンド生成 | 日次バッチ不要。デモ環境で運用しやすい |
| 消化順序 | expiryDate 昇順（古い方から消化） | 労基法の時効ルールに従い、失効を最小化 |
| 取り下げ | WITHDRAWN ステータスで論理的にキャンセル。物理削除しない | 監査証跡の保持 |
| 時間単位の消化 | hours/8 で日数換算してグラントから差し引く | 半日と統一的に管理可能 |
| 半日の時間帯 | 管理しない（0.5日として消化するのみ） | 要件で「時間帯は問わない」と確定 |
| 付与レコードの一意性 | employee_id + grant_date でユニーク | 同一社員に同日付与は発生しない |
