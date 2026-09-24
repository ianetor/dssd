import { Routes } from '@angular/router';
import { EmergenciaDetailComponent } from './components/emergencia-detail/emergencia-detail.component';
import { EmergenciaFormComponent } from './components/emergencia-form/emergencia-form.component';
import { EmergenciasListComponent } from './components/emergencias-list/emergencias-list.component';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { authGuard, roleGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
  },
  {
    path: 'login',
    component: LoginComponent,
  },
  {
    path: 'emergencias',
    component: EmergenciasListComponent,
    canActivate: [authGuard],
  },
  {
    path: 'nueva',
    component: EmergenciaFormComponent,
    canActivate: [authGuard, roleGuard(['OPERADOR_MUNICIPAL', 'COORDINADOR_REGIONAL'])],
  },
  {
    path: 'emergencias/:id',
    component: EmergenciaDetailComponent,
    canActivate: [authGuard],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
