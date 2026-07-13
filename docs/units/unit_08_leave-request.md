# Unit 08: 有給休暇申請・承認

設計: `docs/design/06-paid-leave.md`

## 概要

有給休暇の申請・承認・却下・取り下げフローを担う。
勤怠修正（Unit 05）と同じ承認パターンを踏襲する。

## Phase

**Phase D-2**（Unit 07 の後）

## ユーザーストーリー

| ID | ストーリー | ロール |
|---|---|---|
| LEAVE-01 | 有給休暇を申請できる（対象日・休暇単位・理由を入力） | 全ロール |
| LEAVE-02 | 自分の有給申請の状態を一覧で確認できる | 全ロール |
| LEAVE-04 | 承認前・承認後を問わず申請を取り下げできる | 全ロール |
| LEAVE-05 | 自部署メンバーの有給申請を一覧で確認できる | 上長 |
| LEAVE-06 | 自部署メンバーの有給申請を承認または却下できる | 上長 |
| LEAVE-09 | 承認操作と取り下げ操作が競合した場合、楽観ロックエラーになる | システム |

## テーブル

- `leave_requests`（Flyway V7）

## API

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/leaves/requests` | 有給申請 |
| GET | `/api/leaves/requests` | 自分の申請一覧 |
| PATCH | `/api/leaves/requests/{id}/withdraw` | 申請取り下げ |
| GET | `/api/leaves/requests/pending` | 承認待ち一覧（上長） |
| PATCH | `/api/leaves/requests/{id}/approve` | 承認 |
| PATCH | `/api/leaves/requests/{id}/reject` | 却下 |

## ドメインロジック

### 申請バリデーション

- 残日数チェック（LeaveGrantService と連携）
- 時間単位年休の年間上限チェック（当年度の HOURLY 申請合計 <= 40h）
- 同日重複チェック（同日に PENDING/APPROVED な申請がないこと）
- hours の範囲チェック（HOURLY の場合 1〜7）

### 承認処理

- ステータスを APPROVED に変更
- LeaveGrant の usedDays を加算（消化順序: expiryDate 昇順）
- 承認者を記録

### 却下処理

- ステータスを REJECTED に変更
- 却下理由を記録（レスポンスに含める）

### 取り下げ処理

- PENDING の場合: ステータスを WITHDRAWN に変更するのみ
- APPROVED の場合: ステータスを WITHDRAWN に変更 + LeaveGrant.usedDays を減算

### 楽観ロック

- 承認操作と取り下げの競合は @Version で制御
- 競合時は 409 Conflict

## テスト観点

- [ ] 全日/半日/時間単位の申請が正しく作成される
- [ ] 残日数不足で申請エラー
- [ ] 時間単位年休の年間上限（40h）超過で申請エラー
- [ ] 同日に重複申請でエラー
- [ ] 承認時に usedDays が正しく加算される（消化順序）
- [ ] 承認時に複数付与にまたがる消化が正しく動作する
- [ ] 却下時に usedDays が変わらない
- [ ] PENDING 取り下げ時に usedDays が変わらない
- [ ] APPROVED 取り下げ時に usedDays が正しく減算される
- [ ] 楽観ロック競合で 409 エラー
- [ ] 上長の承認権限チェック（自部署のみ）
- [ ] 上長の自己承認

## 依存

- `employee` ドメイン（申請者・承認者・部署判定）
- Unit 07: `leave` ドメイン（LeaveGrant / LeaveGrantService）

## 成果物

- `V7__create_leave_requests.sql`
- `LeaveRequest.java` (Entity)
- `LeaveRequestRepository.java`
- `LeaveService.java` / `LeaveServiceImpl.java`
- `LeaveRequestCreateRequest.java`, `LeaveRequestResponse.java` 等 (DTO)
- `LeaveController.java`（申請・承認エンドポイント追加）
