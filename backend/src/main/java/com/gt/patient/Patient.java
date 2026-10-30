package com.gt.patient;

import com.gt.sync.dto.PatientPayload;
import com.gt.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @Column(nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @Column(name = "patient_number", nullable = false, length = 20)
    private String patientNumber;
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    @Column(name = "last_name", length = 50)
    private String lastName;
    @Column(nullable = false, length = 10)
    private String gender;
    @Column(nullable = false)
    private short age;
    @Column(nullable = false, length = 10)
    private String mobile;
    @Column(nullable = false, length = 255)
    private String address;
    @Column(name = "referred_by", nullable = false, length = 20)
    private String referredBy;
    @Column(name = "doctor_name", length = 100)
    private String doctorName;
    @Column(name = "consultation_fee_cents", nullable = false)
    private long consultationFeeCents;
    @Column(name = "registration_date", nullable = false)
    private Instant registrationDate;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    protected Patient() {}

    public Patient(UUID id, Tenant tenant) {
        this.id = id;
        this.tenant = tenant;
    }

    public void apply(PatientPayload payload) {
        patientNumber = payload.patientNumber();
        firstName = payload.firstName().trim();
        lastName = payload.lastName() == null || payload.lastName().isBlank() ? null : payload.lastName().trim();
        gender = payload.gender();
        age = payload.age().shortValueExact();
        mobile = payload.mobile();
        address = payload.address().trim();
        referredBy = payload.referredBy();
        doctorName = "DOCTOR".equals(referredBy) && payload.doctorName() != null
                ? payload.doctorName().trim() : null;
        consultationFeeCents = payload.consultationFee().movePointRight(2).longValueExact();
        registrationDate = payload.registrationDate();
        active = payload.active();
        createdAt = payload.createdAt();
        updatedAt = payload.updatedAt();
        deletedAt = payload.deletedAt();
        recordVersion = payload.recordVersion();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getPatientNumber() { return patientNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getGender() { return gender; }
    public short getAge() { return age; }
    public String getMobile() { return mobile; }
    public String getAddress() { return address; }
    public String getReferredBy() { return referredBy; }
    public String getDoctorName() { return doctorName; }
    public long getConsultationFeeCents() { return consultationFeeCents; }
    public Instant getRegistrationDate() { return registrationDate; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getRecordVersion() { return recordVersion; }

    public PatientPayload toPayload() {
        return new PatientPayload(id, patientNumber, firstName, lastName, gender, BigInteger.valueOf(age), mobile,
                address, referredBy, doctorName, BigDecimal.valueOf(consultationFeeCents, 2), registrationDate,
                active, createdAt, updatedAt, deletedAt, recordVersion);
    }
}
