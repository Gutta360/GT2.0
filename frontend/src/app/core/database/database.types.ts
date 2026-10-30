export interface QueryResult<T> { values?: T[] }
export interface ChangeResult { changes?: { changes?: number; lastId?: number } }
export interface SqliteStatement { statement: string; values?: unknown[] }

export interface SqliteExecutor {
  query<T extends object>(statement: string, values?: unknown[]): Promise<QueryResult<T>>;
  run(statement: string, values?: unknown[], transaction?: boolean): Promise<ChangeResult>;
  execute(statements: string, transaction?: boolean): Promise<ChangeResult>;
  executeSet(statements: SqliteStatement[], transaction?: boolean): Promise<ChangeResult>;
}

export type DatabaseState = 'initializing' | 'ready' | 'unsupported' | 'error';
