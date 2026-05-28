import { Component } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { AIService } from './ai.service';
import { MetricsService } from '../../metrics/metrics.service';

@Component({
  selector: 'app-ai',
  templateUrl: './ai.component.html',
  styleUrl: './ai.component.scss',
  standalone: false
})
export class AIComponent {
  form!: FormGroup;
  loading = false;
  _result!: string;
  set result(value: string) {
    this._result = value.replaceAll("```", '')
  }
  get result(): string {
    return this._result;
  }

  constructor(private formBuilder: FormBuilder, private service: AIService, private metricsService: MetricsService) {

  }
  ngOnInit(): void {
    this.form = this.formBuilder.group({
      message: "",
    });
  }
  submit() {
    if (this.form.valid) {
      this.loading = true;
      this.service.test(this.form.value.message).subscribe({
        next: (result: any) => {
          this.result = result.text;
          this.loading = false;
        },
        error: (err: any) => {
          console.error(err);
          alert(err.message);
          this.loading = false;
        }
      });
    } else {
      alert("The form is invalid.");
    }
    this.form.markAllAsTouched();
  }
}
