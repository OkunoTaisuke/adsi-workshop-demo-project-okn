# Unit 07: 有給付与管理

設計: `docs/design/06-paid-leave.md`

## 概要

有給休暇の付与ルール（労基法準拠）と残日数管理を担う。
申請・承認（Unit 08）の前提となるデータ基盤。

## Phase

**Phase D-1**（Unit 05/06 の後。Unit 08 の前提）

## ユーザーストーリー

| ID | ストーリー | ロール |
|---|---|---|
| LEAVE-03 | 自分の有給残日数（当年度・繰越分）を確認できる | 全ロール |

## テーブル

- `leave_grants`（Flyway V6）

## API

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/leaves/balance` | 自分の有給残日数 |

## ドメインロジック

### 付与日数計算（LeaveGrantCalculator）

- 入社日から勤続年数を算出
- 労基法テーブルに基づき付与日数を決定
- 付与基準日の計算（入社6ヶ月後 + 以降1年ごと）

### 年度管理

- fiscalYear: 4月始まり（4/1〜翌3/31）
- 付与日 → 所属年度への読み替え

### 失効・繰越

- expiryDate = 付与年度の翌々年度末
- 有効な付与 = expiryDate > 今日
- 繰越分 = 前年度以前の有効な付与

### オンデマンド付与生成

- `GET /api/leaves/balance` 呼び出し時に未付与分を自動生成
- 付与条件: 現在日 >= 次回付与基準日 かつ 未生成

## テスト観点

- [ ] 勤続年数に応じた正しい付与日数（全パターン: 0.5年〜6.5年以上）
- [ ] 入社日起算の付与基準日計算
- [ ] 年度の正しい判定（3月入社、4月入社、10月入社）
- [ ] 失効判定（2年時効）
- [ ] 繰越分の識別
- [ ] オンデマンド付与の生成（未付与時のみ生成、二重生成しない）
- [ ] 残日数計算（付与 - 消化 = 残）

## 依存

- `employee` ドメイン（Employee エンティティ・hireDate）

## 成果物

- `V6__create_leave_grants.sql`
- `LeaveGrant.java` (Entity)
- `LeaveGrantRepository.java`
- `LeaveGrantService.java` / `LeaveGrantServiceImpl.java`
- `LeaveGrantCalculator.java`（付与日数・基準日計算）
- `LeaveBalance.java` (VO / Response)
- `LeaveType.java`, `LeaveRequestStatus.java` (Enum — Unit 08 でも使用)
- `LeaveController.java`（balance エンドポイントのみ）
