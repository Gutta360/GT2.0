import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import type { PatientRecord, PatientRequest } from '../models/patient.model';

@Component({
  selector: 'app-patient-form', imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form class="patient-form" [formGroup]="form" (ngSubmit)="submit()">
      <div class="form-grid">
        <label class="wide">Registration Date & Time *<input type="datetime-local" formControlName="registrationDate" /></label>
        <label>First Name *<input type="text" formControlName="firstName" maxlength="50" placeholder="Enter first name" /></label>
        <label>Last Name<input type="text" formControlName="lastName" maxlength="50" placeholder="Optional" /></label>
        <fieldset class="wide"><legend>Gender *</legend><div class="radio-row">
          <label><input type="radio" formControlName="gender" value="MALE" />Male</label>
          <label><input type="radio" formControlName="gender" value="FEMALE" />Female</label>
          <label><input type="radio" formControlName="gender" value="OTHER" />Other</label>
        </div></fieldset>
        <label>Age *<input type="number" inputmode="numeric" formControlName="age" min="1" max="120" placeholder="Enter age" /></label>
        <label>Mobile Number *<input type="tel" inputmode="numeric" formControlName="mobile" maxlength="10" placeholder="10-digit mobile number" /></label>
        <label class="wide">Address *<textarea formControlName="address" rows="3" maxlength="255" placeholder="Enter address"></textarea></label>
        <fieldset class="wide"><legend>Referred By *</legend><div class="radio-row referral-row">
          <label><input type="radio" formControlName="referredBy" value="DOCTOR" />Doctor</label>
          <label><input type="radio" formControlName="referredBy" value="PATIENT" />Patient</label>
          <label><input type="radio" formControlName="referredBy" value="ONLINE" />Online</label>
          <label><input type="radio" formControlName="referredBy" value="SELF" />Self</label>
          <label><input type="radio" formControlName="referredBy" value="OTHER" />Other</label>
        </div></fieldset>
        @if (isDoctorReferral) { <label class="wide">Doctor Name *<input type="text" formControlName="doctorName" maxlength="100" placeholder="Enter doctor name" /></label> }
        <label>Consultation Fee *<input type="number" inputmode="decimal" formControlName="consultationFee" min="0" max="99999999.99" step="0.01" /></label>
      </div>
      @if (form.invalid && form.touched) { <p class="validation-message">Complete all required fields with valid values.</p> }
      <footer class="form-footer"><button type="submit" class="primary-button" [disabled]="loading">{{ loading ? 'Saving…' : mode === 'edit' ? 'Update' : 'Create' }}</button></footer>
    </form>`
})
export class PatientFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialData: PatientRecord | null = null;
  @Input() loading = false;
  @Output() readonly formSubmit = new EventEmitter<PatientRequest>();
  readonly form = this.fb.group({
    registrationDate: ['', Validators.required],
    firstName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50), Validators.pattern(/^[A-Za-z ]+$/)]],
    lastName: ['', [Validators.maxLength(50), Validators.pattern(/^[A-Za-z ]*$/)]],
    gender: ['', Validators.required], age: [null as number | null, [Validators.required, Validators.min(1), Validators.max(120)]],
    mobile: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    address: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(255)]],
    referredBy: ['', Validators.required], doctorName: [''],
    consultationFee: [0, [Validators.required, this.feeValidator]]
  });
  ngOnInit(): void {
    this.form.controls.referredBy.valueChanges.subscribe(value => this.updateDoctorValidation(value));
    if (this.initialData) {
      this.form.patchValue({ ...this.initialData, registrationDate: this.toLocalDateTime(this.initialData.registrationDate) });
      this.updateDoctorValidation(this.initialData.referredBy);
    } else this.form.controls.registrationDate.setValue(this.toLocalDateTime(new Date().toISOString()));
  }
  get isDoctorReferral(): boolean { return this.form.controls.referredBy.value === 'DOCTOR'; }
  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    this.formSubmit.emit({
      firstName: value.firstName!, lastName: value.lastName || null, gender: value.gender as PatientRequest['gender'],
      age: Number(value.age), mobile: value.mobile!, address: value.address!, referredBy: value.referredBy as PatientRequest['referredBy'],
      doctorName: value.doctorName || null, consultationFee: Number(value.consultationFee), registrationDate: new Date(value.registrationDate!).toISOString()
    });
  }
  private updateDoctorValidation(referral: string | null): void {
    const control = this.form.controls.doctorName;
    if (referral === 'DOCTOR') control.setValidators([Validators.required, Validators.minLength(2), Validators.maxLength(100)]);
    else { control.clearValidators(); control.setValue('', { emitEvent: false }); }
    control.updateValueAndValidity({ emitEvent: false });
  }
  private feeValidator(control: AbstractControl): ValidationErrors | null {
    const amount = Number(control.value); return Number.isFinite(amount) && amount >= 0 && amount <= 99_999_999.99 && Math.round(amount * 100) === amount * 100 ? null : { fee: true };
  }
  private toLocalDateTime(value: string): string {
    const date = new Date(value); const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000); return local.toISOString().slice(0, 16);
  }
}
