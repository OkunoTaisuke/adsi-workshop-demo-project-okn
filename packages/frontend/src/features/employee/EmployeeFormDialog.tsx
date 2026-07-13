"use client";

import { useEffect, useState } from "react";
import { FormDialog } from "@/components/FormDialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  DepartmentSummary,
  EmployeeCreateRequest,
  EmployeeResponse,
  EmployeeUpdateRequest,
} from "./employee-api";

interface EmployeeFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  employee: EmployeeResponse | null;
  departments: DepartmentSummary[];
  onSubmitCreate: (request: EmployeeCreateRequest) => void;
  onSubmitUpdate: (id: string, request: EmployeeUpdateRequest) => void;
  isSubmitting: boolean;
}

interface FormState {
  name: string;
  email: string;
  password: string;
  departmentId: string;
  role: "ADMIN" | "EMPLOYEE";
  hireDate: string;
}

const INITIAL_FORM: FormState = {
  name: "",
  email: "",
  password: "",
  departmentId: "",
  role: "EMPLOYEE",
  hireDate: "",
};

export function EmployeeFormDialog({
  open,
  onOpenChange,
  mode,
  employee,
  departments,
  onSubmitCreate,
  onSubmitUpdate,
  isSubmitting,
}: EmployeeFormDialogProps) {
  const [form, setForm] = useState<FormState>(INITIAL_FORM);

  useEffect(() => {
    if (open && mode === "edit" && employee) {
      setForm({
        name: employee.name,
        email: employee.email,
        password: "",
        departmentId: employee.departmentId,
        role: employee.role,
        hireDate: employee.hireDate,
      });
    }
    if (open && mode === "create") {
      setForm(INITIAL_FORM);
    }
  }, [open, mode, employee]);

  const handleSubmit = () => {
    if (mode === "create") {
      onSubmitCreate({
        name: form.name,
        email: form.email,
        password: form.password,
        departmentId: form.departmentId,
        role: form.role,
        hireDate: form.hireDate,
      });
    } else if (employee) {
      onSubmitUpdate(employee.id, {
        name: form.name,
        email: form.email,
        departmentId: form.departmentId,
        role: form.role,
        hireDate: form.hireDate,
      });
    }
  };

  const title = mode === "create" ? "社員登録" : "社員編集";

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      onSubmit={handleSubmit}
      submitLabel={mode === "create" ? "登録" : "更新"}
      isSubmitting={isSubmitting}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="employee-name">名前</Label>
          <Input
            id="employee-name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="employee-email">メール</Label>
          <Input
            id="employee-email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
        </div>

        {mode === "create" && (
          <div className="space-y-2">
            <Label htmlFor="employee-password">パスワード</Label>
            <Input
              id="employee-password"
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
            />
          </div>
        )}

        <div className="space-y-2">
          <Label>部署</Label>
          <Select
            value={form.departmentId || undefined}
            onValueChange={(value) => setForm({ ...form, departmentId: value ?? "" })}
          >
            <SelectTrigger>
              <SelectValue>
                {departments.find((d) => d.id === form.departmentId)?.name ?? "部署を選択"}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {departments.map((dept) => (
                <SelectItem key={dept.id} value={dept.id}>
                  {dept.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>ロール</Label>
          <Select
            value={form.role}
            onValueChange={(value) =>
              setForm({
                ...form,
                role: (value as "ADMIN" | "EMPLOYEE") ?? "EMPLOYEE",
              })
            }
          >
            <SelectTrigger>
              <SelectValue>
                {form.role === "ADMIN" ? "管理者" : "一般"}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="EMPLOYEE">一般</SelectItem>
              <SelectItem value="ADMIN">管理者</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="employee-hireDate">入社日</Label>
          <Input
            id="employee-hireDate"
            type="date"
            value={form.hireDate}
            onChange={(e) => setForm({ ...form, hireDate: e.target.value })}
            required
          />
        </div>
      </div>
    </FormDialog>
  );
}
