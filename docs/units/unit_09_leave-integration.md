# Unit 09: 有給休暇 — 集計・勤務時間統合

設計: `docs/design/06-paid-leave.md`

## 概要

有給休暇の承認結果を既存の勤務時間計算・月次集計・管理者画面に統合する。

## Phase

**Phase D-3**（Unit 07, 08 の後）

## ユーザーストーリー

| ID | ストーリー | ロール |
|---|---|---|
| LEAVE-07 | 全社員の有給取得状況・残日数を確認できる | 管理者 |
| LEAVE-08 | 承認された有給休暇日は月次集計で欠勤扱いにならない | システム |

## テーブル

なし（既存テーブル + leave_grants / leave_requests を参照）

## API

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/leaves/summary` | 全社員の有給取得状況（管理者） |

## 既存機能の変更

### 1. 勤務時間計算（WorkDuration）の変更

`AttendanceServiceImpl` の勤務時間計算に有給の影響を統合:

- 半日休暇の日: 残業判定基準を 8h → 4h に変更
- 時間単位休暇 (n時間) の日: 残業判定基準を 8h → (8-n)h に変更
- 全日休暇の日: 打刻なしでも勤務時間計算対象外（欠勤にもしない）

### 2. 月次集計（ReportService）の変更

`ReportServiceImpl` の月次集計に有給情報を追加:

- 有給取得日数（当月）: 当月の APPROVED な LeaveRequest の消化日数合計
- 欠勤日数の修正: 営業日 − 出勤日数 − 全日有給取得日数
- 有給残日数: LeaveBalance.remainingDays（各社員の現在残日数）

### 3. 月次集計レスポンス（MonthlyReportResponse）の拡張

追加フィールド:
- `paidLeaveDays` (BigDecimal): 当月有給取得日数
- `remainingLeaveDays` (BigDecimal): 有給残日数

### 4. CSV/PDF 帳票の拡張

CSV カラム追加: 有給取得日数, 有給残日数
PDF レイアウト: 同上のカラム追加

## テスト観点

- [ ] 半日休暇の日の残業計算（4h超過が残業）
- [ ] 時間単位休暇の日の残業計算（(8-n)h超過が残業）
- [ ] 全日休暇の日が欠勤にカウントされない
- [ ] 月次集計の有給取得日数が正しい（全日=1.0, 半日=0.5, 時間=h/8）
- [ ] 管理者の有給取得状況サマリーが正しい
- [ ] CSV に有給関連カラムが出力される
- [ ] PDF に有給関連カラムが出力される

## 依存

- Unit 07: LeaveGrant（残日数参照）
- Unit 08: LeaveRequest（承認済み申請の参照）
- Unit 04: attendance ドメイン（勤務時間計算の修正）
- Unit 06: report ドメイン（月次集計の修正）

## 成果物

- `AttendanceServiceImpl.java`（勤務時間計算の修正）
- `ReportServiceImpl.java`（月次集計の修正）
- `MonthlyReportResponse.java`（フィールド追加）
- `CsvExportServiceImpl.java`（カラム追加）
- `PdfExportServiceImpl.java`（カラム追加）
- `LeaveController.java`（summary エンドポイント追加）
- `LeaveSummaryResponse.java`
