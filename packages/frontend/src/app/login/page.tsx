import { LoginForm } from "@/features/auth/LoginForm";

export default function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-full max-w-sm space-y-6 rounded-lg border bg-card p-8 shadow-sm">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold">勤怠管理システム</h1>
          <p className="text-sm text-muted-foreground">メールアドレスとパスワードでログイン</p>
        </div>
        <LoginForm />
      </div>
    </div>
  );
}
