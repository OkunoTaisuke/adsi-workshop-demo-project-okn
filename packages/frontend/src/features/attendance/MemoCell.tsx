"use client";

import { Pencil, Trash2, Check, X } from "lucide-react";
import { useState } from "react";
import type { AttendanceRecordResponse } from "./attendance-api";
import { useUpdateMemo } from "./useAttendance";

interface MemoCellProps {
  record: AttendanceRecordResponse;
  editable: boolean;
}

export function MemoCell({ record, editable }: MemoCellProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(record.memo ?? "");
  const updateMemo = useUpdateMemo();

  const handleSave = () => {
    const value = draft.trim() || null;
    updateMemo.mutate(
      { recordId: record.id, memo: value },
      { onSuccess: () => setIsEditing(false) },
    );
  };

  const handleDelete = () => {
    updateMemo.mutate(
      { recordId: record.id, memo: null },
      {
        onSuccess: () => {
          setDraft("");
          setIsEditing(false);
        },
      },
    );
  };

  const handleCancel = () => {
    setDraft(record.memo ?? "");
    setIsEditing(false);
  };

  if (isEditing) {
    return (
      <div className="flex items-center gap-1">
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={200}
          className="w-full min-w-[120px] rounded border px-2 py-1 text-sm bg-background"
          autoFocus
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSave();
            if (e.key === "Escape") handleCancel();
          }}
        />
        <button
          type="button"
          onClick={handleSave}
          disabled={updateMemo.isPending}
          className="p-1 text-green-600 hover:text-green-700"
        >
          <Check className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={handleCancel}
          className="p-1 text-muted-foreground hover:text-foreground"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-1 group">
      <span className="text-sm">{record.memo || ""}</span>
      {editable && (
        <span className="hidden group-hover:inline-flex items-center gap-1">
          <button
            type="button"
            onClick={() => setIsEditing(true)}
            className="p-1 text-muted-foreground hover:text-foreground"
          >
            <Pencil className="h-3 w-3" />
          </button>
          {record.memo && (
            <button
              type="button"
              onClick={handleDelete}
              disabled={updateMemo.isPending}
              className="p-1 text-muted-foreground hover:text-red-600"
            >
              <Trash2 className="h-3 w-3" />
            </button>
          )}
        </span>
      )}
    </div>
  );
}
