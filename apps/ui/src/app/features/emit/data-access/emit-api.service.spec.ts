import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EmitApi } from './emit-api.service';
import { environment } from '../../../../environments/environment';
describe('Fiscal emission wire contract', () => {
  it('preserves an external id as the idempotency key and sends the original payload', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(EmitApi),
      requests = TestBed.inject(HttpTestingController);
    const payload = { externalId: 'synthetic-operation', businessCode: 'B11111111111', total: 100 };
    api.emitDocument(payload).subscribe();
    const request = requests.expectOne(environment.apiUrl + '/documents/emit');
    expect(request.request.headers.get('Idempotency-Key')).toBe(payload.externalId);
    expect(request.request.body).toEqual(payload);
    request.flush({});
    requests.verify();
  });
});
