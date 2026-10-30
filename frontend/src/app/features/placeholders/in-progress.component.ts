import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-in-progress',
  template: `<section class="placeholder-page"><div class="placeholder-card"><span class="progress-icon">◌</span><p class="eyebrow">{{ title }}</p><h1>In Progress</h1><p>This module is reserved for a future delivery phase.</p></div></section>`
})
export class InProgressComponent { readonly title = inject(ActivatedRoute).snapshot.data['title'] as string; }
