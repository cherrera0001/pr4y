'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  LayoutDashboard,
  Users,
  FileText,
  LogOut,
} from 'lucide-react';
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarInset,
} from '@/components/ui/sidebar';

const navItems = [
  { href: '/admin', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/admin/users', label: 'Usuarios', icon: Users },
  { href: '/admin/content', label: 'Contenidos', icon: FileText },
];

export default function AdminPanelLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const handleLogout = async () => {
    await fetch('/api/admin/session', { method: 'DELETE' });
    router.push('/admin/login');
    router.refresh();
  };

  return (
    <SidebarProvider>
      <Sidebar className="glass border-r border-sidebar-border backdrop-blur-xl">
        <SidebarHeader className="border-b border-sidebar-border">
          <SidebarMenuButton asChild>
            <Link href="/admin">
              <span className="font-semibold text-sidebar-foreground">PR4Y Admin</span>
            </Link>
          </SidebarMenuButton>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {navItems.map((item) => (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton
                      asChild
                      isActive={pathname === item.href || (item.href !== '/admin' && pathname.startsWith(item.href))}
                    >
                      <Link href={item.href}>
                        <item.icon className="size-4" />
                        <span>{item.label}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter className="border-t border-sidebar-border">
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton onClick={handleLogout}>
                <LogOut className="size-4" />
                <span>Cerrar sesión</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        <main className="flex-1 p-6 md:p-8 min-h-screen bg-slate-950/50">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
