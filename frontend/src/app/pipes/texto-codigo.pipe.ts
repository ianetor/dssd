import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'textoCodigo', standalone: true })
export class TextoCodigoPipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    return (valor ?? '').replace(/_/g, ' ');
  }
}
