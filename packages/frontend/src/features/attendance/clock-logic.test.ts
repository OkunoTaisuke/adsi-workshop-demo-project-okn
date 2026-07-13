import { describe, expect, it } from "vitest";

type AttendanceStatus = "NOT_CLOCKED_IN" | "CLOCKED_IN" | "CLOCKED_OUT";

function canClockIn(status: AttendanceStatus): boolean {
  return status !== "CLOCKED_IN";
}

function canClockOut(status: AttendanceStatus): boolean {
  return status === "CLOCKED_IN";
}

describe("打刻ボタン有効/無効ロジック", () => {
  describe("canClockIn", () => {
    it("NOT_CLOCKED_IN: 出勤可能", () => {
      expect(canClockIn("NOT_CLOCKED_IN")).toBe(true);
    });

    it("CLOCKED_IN: 出勤不可（ATT-04）", () => {
      expect(canClockIn("CLOCKED_IN")).toBe(false);
    });

    it("CLOCKED_OUT: 再出勤可能（ATT-06）", () => {
      expect(canClockIn("CLOCKED_OUT")).toBe(true);
    });
  });

  describe("canClockOut", () => {
    it("CLOCKED_IN: 退勤可能", () => {
      expect(canClockOut("CLOCKED_IN")).toBe(true);
    });

    it("NOT_CLOCKED_IN: 退勤不可", () => {
      expect(canClockOut("NOT_CLOCKED_IN")).toBe(false);
    });

    it("CLOCKED_OUT: 退勤不可", () => {
      expect(canClockOut("CLOCKED_OUT")).toBe(false);
    });
  });
});
