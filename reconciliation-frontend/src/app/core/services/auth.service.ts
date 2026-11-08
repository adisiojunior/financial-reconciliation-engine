import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly baseUrl = 'http://localhost:8080/api/v1/auth';
  private readonly TOKEN_KEY = 'jwt_reconciliation_token';
  private authState$ = new BehaviorSubject<boolean>(this.hasToken());

  public isAuthenticated$ = this.authState$.asObservable();

  constructor(private http: HttpClient) {}

  login(username: string = 'admin', role: 'ROLE_ADMIN' | 'ROLE_OPERATOR' = 'ROLE_ADMIN'): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, { username, role }).pipe(
      tap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.accessToken);
        this.authState$.next(true);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.authState$.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }
}