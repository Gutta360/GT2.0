export type ModuleKey = 'home' | 'patients' | 'appointments' | 'treatments' | 'payments' | 'pharmacy';

export interface ClientBrandConfig {
  readonly applicationName: string;
  readonly businessName: string;
  readonly tagline: string;
  readonly logoPath: string;
  readonly homeImagePath: string;
  readonly primaryColor: string;
  readonly secondaryColor: string;
  readonly navigationLabels: Readonly<Record<ModuleKey, string>>;
  readonly enabledModules: readonly ModuleKey[];
}

export const CLIENT_BRAND: ClientBrandConfig = {
  applicationName: 'GT2.0',
  businessName: 'Gutta Dental Clinic',
  tagline: 'Dental Clinic Management System',
  logoPath: 'branding/client-logo.svg',
  homeImagePath: 'branding/home-branding.svg',
  primaryColor: '#000000',
  secondaryColor: '#5f6f78',
  navigationLabels: {
    home: 'Home', patients: 'Patient', appointments: 'Appointment',
    treatments: 'Treatment', payments: 'Payment', pharmacy: 'Pharmacy'
  },
  enabledModules: ['home', 'patients', 'appointments', 'treatments', 'payments', 'pharmacy']
};
