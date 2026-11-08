export type TransactionStatus = 'PENDING' | 'PROCESSING' | 'RECONCILED' | 'FAILED';

export interface Transaction {
  readonly id: string;
  readonly externalReference: string;
  readonly amount: number;
  readonly currency: string;
  readonly status: TransactionStatus;
  readonly version: number;
  readonly createdAt: string;
}

export interface CreateTransactionCommand {
  externalReference: string;
  amount: number;
  currency: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}