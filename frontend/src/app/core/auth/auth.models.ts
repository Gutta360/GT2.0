export interface LocalUser { id: string; username: string; displayName: string }
export interface LocalUserRow {
  id: string; username: string; display_name: string; password_salt: string;
  password_hash: string; password_iterations: number; enabled: number;
}
