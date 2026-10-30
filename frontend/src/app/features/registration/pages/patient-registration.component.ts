import { ChangeDetectorRef, Component } from '@angular/core';
import { PatientFormComponent } from '../components/patient-form.component';
import type { PatientRequest } from '../models/patient.model';
import { PatientService } from '../services/patient.service';

@Component({
  selector: 'app-patient-registration', imports: [PatientFormComponent],
  template: `<section class="content-card"><header class="page-header"><span class="eyebrow">Patient</span><h1>Register Patient</h1><p>Create a patient record on this tablet.</p></header><app-patient-form [loading]="loading" (formSubmit)="create($event)" /></section>@if (message) { <div class="toast" [class.error]="error" role="alert">{{ message }}</div> }`
})
export class PatientRegistrationComponent {
  loading = false; message = ''; error = false;
  constructor(private readonly patients: PatientService, private readonly changeDetector: ChangeDetectorRef) {}
  async create(request: PatientRequest): Promise<void> {
    this.loading = true; this.message = '';
    try { const patient = await this.patients.create(request); this.error = false; this.message = `${patient.patientNumber} registered successfully.`; }
    catch (error) { this.error = true; this.message = error instanceof Error ? error.message : 'Could not register patient.'; }
    finally {
      this.loading = false;
      this.changeDetector.markForCheck();
      setTimeout(() => { this.message = ''; this.changeDetector.markForCheck(); }, 5000);
    }
  }
}
