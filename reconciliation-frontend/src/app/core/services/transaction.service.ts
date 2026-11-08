import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, timer, switchMap, shareReplay, Subject, merge } from 'rxjs';
import { Transaction, CreateTransactionCommand, Page } from '../../../../src/app/shared/models/transaction.model';

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private readonly apiUrl = 'http://localhost:8080/api/v1/transactions';
  private refreshTrigger$ = new Subject<void>();

  constructor(private http: HttpClient) {}

  getTransactions(page: number = 0, size: number = 10): Observable<Page<Transaction>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Transaction>>(this.apiUrl, { params });
  }

  createTransaction(command: CreateTransactionCommand): Observable<Transaction> {
    return this.http.post<Transaction>(this.apiUrl, command);
  }

  pollTransactions(page: number = 0, size: number = 10, intervalMs: number = 3000): Observable<Page<Transaction>> {
    return merge(timer(0, intervalMs), this.refreshTrigger$).pipe(
      switchMap(() => this.getTransactions(page, size)),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }

  triggerRefresh(): void {
    this.refreshTrigger$.next();
  }
}