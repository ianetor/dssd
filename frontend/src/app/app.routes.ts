import { Routes } from '@angular/router';
import { EmergenciaDetailComponent } from './components/emergencia-detail/emergencia-detail.component';
import { EmergenciaFormComponent } from './components/emergencia-form/emergencia-form.component';
import { EmergenciasListComponent } from './components/emergencias-list/emergencias-list.component';
import { LoginComponent } from './components/login/login.component';
import { PortalOngComponent } from './components/portal-ong/portal-ong.component';
import { authGuard, guestGuard, roleGuard, rootGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [rootGuard],
    children: [],
  },
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [guestGuard],
  },
  {
    path: 'operador',
    component: EmergenciaFormComponent,
    canActivate: [authGuard, roleGuard(['OPERADOR_MUNICIPAL'])],
  },
  {
    path: 'nueva',
    component: EmergenciaFormComponent,
    canActivate: [authGuard, roleGuard(['OPERADOR_MUNICIPAL'])],
  },
  {
    path: 'coordinador',
    component: EmergenciaDetailComponent,
    canActivate: [authGuard, roleGuard(['COORDINADOR_REGIONAL'])],
  },
  {
    path: 'emergencias/:id',
    component: EmergenciaDetailComponent,
    canActivate: [authGuard, roleGuard(['COORDINADOR_REGIONAL', 'AUDITOR_DIRECTIVO'])],
  },
  {
    path: 'portal-ong',
    component: PortalOngComponent,
    canActivate: [authGuard, roleGuard(['REPRESENTANTE_ONG'])],
  },
  {
    path: 'emergencias',
    component: EmergenciasListComponent,
    canActivate: [authGuard, roleGuard(['COORDINADOR_REGIONAL', 'AUDITOR_DIRECTIVO'])],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
