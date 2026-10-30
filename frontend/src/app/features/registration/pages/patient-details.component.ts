import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import type { PatientRecord, PatientSummary } from '../models/patient.model';
import { PatientService } from '../services/patient.service';

@Component({
  selector: 'app-patient-details', imports: [FormsModule],
  template: `
    <section class="content-card">
      <header class="page-header"><span class="eyebrow">Patient</span><h1>Patient Details</h1><p>Search records stored on this tablet.</p></header>
      <div class="search-panel"><label for="patient-search">Patient Search</label><input id="patient-search" [(ngModel)]="query" (ngModelChange)="search()" placeholder="Search by ID, name, or mobile" /></div>
      <div class="details-layout">
        <div class="patient-list" aria-label="Patient search results">
          @for (item of results; track item.id) { <button type="button" [class.selected]="item.patientNumber === selected?.patientNumber" (click)="select(item)"><strong>{{ item.patientNumber }}</strong><span>{{ item.firstName }} {{ item.lastName }}</span><small>{{ item.mobile }}</small></button> }
          @empty { <div class="empty-state">No patients found.</div> }
        </div>
        <div class="patient-detail">
          @if (selected) {
            <div class="detail-heading"><div><span class="eyebrow">{{ selected.patientNumber }}</span><h2>{{ selected.firstName }} {{ selected.lastName }}</h2></div><button type="button" class="secondary-button" (click)="edit()">Edit</button></div>
            <dl>
              <div><dt>Gender</dt><dd>{{ label(selected.gender) }}</dd></div><div><dt>Age</dt><dd>{{ selected.age }}</dd></div>
              <div><dt>Mobile</dt><dd>{{ selected.mobile }}</dd></div><div><dt>Registration</dt><dd>{{ formatDate(selected.registrationDate) }}</dd></div>
              <div class="wide"><dt>Address</dt><dd>{{ selected.address }}</dd></div><div><dt>Referred By</dt><dd>{{ label(selected.referredBy) }} @if (selected.doctorName) { ({{ selected.doctorName }}) }</dd></div>
              <div><dt>Consultation Fee</dt><dd>{{ selected.consultationFee === 0 ? 'Free' : '₹' + selected.consultationFee.toFixed(2) }}</dd></div>
            </dl>
          } @else { <div class="empty-state detail-empty"><h2>Select a patient</h2><p>Choose a record to view its details.</p></div> }
        </div>
      </div>
    </section>`
})
export class PatientDetailsComponent implements OnInit {
  query = ''; results: PatientSummary[] = []; selected: PatientRecord | null = null; private searchGeneration = 0;
  constructor(private readonly patients: PatientService, private readonly router: Router, private readonly changeDetector: ChangeDetectorRef) {}
  ngOnInit(): void { void this.search(); }
  async search(): Promise<void> { const generation = ++this.searchGeneration; const results = await this.patients.search(this.query); if (generation === this.searchGeneration) { this.results = results; this.changeDetector.markForCheck(); } }
  async select(item: PatientSummary): Promise<void> { this.selected = await this.patients.get(item.patientNumber); this.changeDetector.markForCheck(); }
  edit(): void { if (this.selected) void this.router.navigate(['/patients', this.selected.patientNumber, 'edit']); }
  label(value: string): string { return value.charAt(0) + value.slice(1).toLowerCase(); }
  formatDate(value: string): string { return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }
}
