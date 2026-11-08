-- Run this once in Supabase: SQL Editor -> New query -> paste -> Run.
-- One row per person holds their whole schedule. Each person can only see and change their own row.
create table if not exists public.schedules (
  user_id uuid primary key references auth.users (id) on delete cascade,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.schedules enable row level security;

drop policy if exists "read own schedule" on public.schedules;
drop policy if exists "add own schedule" on public.schedules;
drop policy if exists "change own schedule" on public.schedules;

create policy "read own schedule" on public.schedules
  for select using (auth.uid() = user_id);
create policy "add own schedule" on public.schedules
  for insert with check (auth.uid() = user_id);
create policy "change own schedule" on public.schedules
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Live sync: changes on your phone show up on your PC right away (and the other way round).
alter publication supabase_realtime add table public.schedules;
