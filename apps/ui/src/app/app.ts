import { Component, OnInit } from '@angular/core';
import { ToggleService } from './common/header/toggle.service';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './common/header/header.component';
import { SidebarComponent } from './common/sidebar/sidebar.component';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { BuildInfoService } from './services/build-info.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, SidebarComponent, HeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class AppComponent implements OnInit {
  isToggled = false;
  isLogin = false;

  constructor(private toggleService: ToggleService, private router: Router, public buildInfo: BuildInfoService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
    this.isLogin = this.router.url.startsWith('/login');
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      this.isLogin = (event as NavigationEnd).urlAfterRedirects.startsWith('/login');
    });
  }

  ngOnInit(): void { this.buildInfo.load(); }
}
