import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, takeUntil } from 'rxjs';

// Ajuste dos 3 níveis para a pasta services do módulo:
import { TransactionService } from '../../../../../app/core/services/transaction.service';

// Ajuste dos 5 níveis até à raiz do app para core e shared:
import { AuthService } from '../../../../../app/core/services/auth.service';
import { Transaction, Page } from '../../../../../app/shared/models/transaction.model';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  transactions: Transaction[] = [];
  transactionPage?: Page<Transaction>;
  currentPage: number = 0;
  pageSize: number = 10;
  isSubmitting: boolean = false;
  authStatus: boolean = false;

  transactionForm!: FormGroup;
  private destroy$ = new Subject<void>();

  constructor(
    private transactionService: TransactionService,
    private authService: AuthService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.checkAuthenticationAndStart();
  }

  private initForm(): void {
    this.transactionForm = this.fb.group({
      externalReference: ['', [Validators.required, Validators.minLength(3)]],
      amount: [null, [Validators.required, Validators.min(0.01)]],
      currency: ['BRL', Validators.required]
    });
  }

  private checkAuthenticationAndStart(): void {
    if (!this.authService.getToken()) {
      this.authService.login('admin', 'ROLE_ADMIN').subscribe({
        next: () => {
          this.authStatus = true;
          this.startPolling();
        },
        error: (err: HttpErrorResponse) => {
          console.error('Erro na autenticacao inicial:', err);
        }
      });
    } else {
      this.authStatus = true;
      this.startPolling();
    }
  }

  private startPolling(): void {
    this.transactionService
      .pollTransactions(this.currentPage, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page: Page<Transaction>) => {
          this.transactionPage = page;
          this.transactions = page.content;
        },
        error: (err: HttpErrorResponse) => {
          console.error('Erro na requisicao paginada:', err);
        }
      });
  }

  onSubmit(): void {
    if (this.transactionForm.invalid || this.isSubmitting) return;

    this.isSubmitting = true;
    this.transactionService.createTransaction(this.transactionForm.value).subscribe({
      next: () => {
        this.transactionForm.reset({ currency: 'BRL' });
        this.isSubmitting = false;
        this.transactionService.triggerRefresh();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Erro ao despachar transacao:', err);
        this.isSubmitting = false;
      }
    });
  }

  get countReconciled(): number {
    return this.transactions.filter(t => t.status === 'RECONCILED').length;
  }

  get countPending(): number {
    return this.transactions.filter(t => t.status === 'PENDING' || t.status === 'PROCESSING').length;
  }

  get countFailed(): number {
    return this.transactions.filter(t => t.status === 'FAILED').length;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}