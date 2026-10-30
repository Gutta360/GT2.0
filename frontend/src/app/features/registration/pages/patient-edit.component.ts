import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PatientFormComponent } from '../components/patient-form.component';
import type { PatientRecord, PatientRequest } from '../models/patient.model';
import { PatientService } from '../services/patient.service';

@Component({
  selector: 'app-patient-edit', imports: [PatientFormComponent],
  template: `<section class="content-card"><header class="page-header"><span class="eyebrow">{{ patientNumber }}</span><h1>Edit Patient Details</h1></header>@if (patient) { <app-patient-form mode="edit" [initialData]="patient" [loading]="loading" (formSubmit)="update($event)" /><footer class="form-footer"><button type="button" class="secondary-button" (click)="close()">Close</button></footer> } @else { <div class="empty-state">{{ message || 'Loading patient…' }}</div> }</section>@if (message && patient) { <div class="toast" [class.error]="error">{{ message }}</div> }`
})
export class PatientEditComponent implements OnInit {
  readonly patientNumber: string; patient: PatientRecord | null = null; loading = false; message = ''; error = false;
  constructor(private readonly route: ActivatedRoute, private readonly router: Router, private readonly patients: PatientService, private readonly changeDetector: ChangeDetectorRef) {
    this.patientNumber = this.route.snapshot.paramMap.get('patientNumber') ?? '';
  }
  async ngOnInit(): Promise<void> { this.patient = await this.patients.get(this.patientNumber); if (!this.patient) this.message = 'Patient was not found.'; this.changeDetector.markForCheck(); }
  async update(request: PatientRequest): Promise<void> { this.loading = true; try { this.patient = await this.patients.update(this.patientNumber, request); this.error = false; this.message = 'Updated successfully.'; } catch (error) { this.error = true; this.message = error instanceof Error ? error.message : 'Update failed.'; } finally { this.loading = false; this.changeDetector.markForCheck(); } }
  close(): void { void this.router.navigate(['/patients/details']); }
}
