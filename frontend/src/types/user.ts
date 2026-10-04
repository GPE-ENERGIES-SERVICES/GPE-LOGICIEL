export type Role = "USER" | "ADMIN";

export type CurrentUser = {
  id: number;
  email: string;
  displayName: string;
  role: Role;
};

export type User = {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
};
