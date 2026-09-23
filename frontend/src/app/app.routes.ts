import { Routes } from '@angular/router';
import { EmergenciaDetailComponent } from './components/emergencia-detail/emergencia-detail.component';
import { EmergenciaFormComponent } from './components/emergencia-form/emergencia-form.component';
import { EmergenciasListComponent } from './components/emergencias-list/emergencias-list.component';
import { HomeComponent } from './components/home/home.component';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
  },
  {
    path: 'emergencias',
    component: EmergenciasListComponent,
  },
  {
    path: 'nueva',
    component: EmergenciaFormComponent,
  },
  {
    path: 'emergencias/:id',
    component: EmergenciaDetailComponent,
  },
  {
    path: '**',
    redirectTo: '',
  },
];
