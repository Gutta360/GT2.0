import { Component } from '@angular/core';
import { CLIENT_BRAND } from '../../core/configuration/client-brand.config';

@Component({
  selector: 'app-home',
  template: `<section class="home-page"><div class="home-card"><img [src]="brand.homeImagePath" [alt]="brand.businessName" /><div><span class="eyebrow">Welcome</span><h1>{{ brand.businessName }}</h1><p>{{ brand.tagline }}</p><small>Offline workspace ready</small></div></div></section>`
})
export class HomeComponent { readonly brand = CLIENT_BRAND; }
