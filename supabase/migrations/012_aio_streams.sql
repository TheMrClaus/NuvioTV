-- Reconstructed from supabase_migrations.schema_migrations (version='012').
-- The original 012 was applied to the live DB before being committed to the repo.
-- Tracked here so 'supabase db push' can verify local matches remote and proceed.

-- AIOStreams per-profile bridge.
-- OmnioTV self-hosts the upstream Viren070/AIOStreams addon
-- (see infra/aiostreams-selfhost/). This table maps
-- (supabase user, profile) → upstream user UUID + the password we minted on
-- first save. Same shape as aio_metadata_links, arrived at on day one rather
-- than via the 005 → 006 → 010 evolution.
--
-- Provider creds (debrid keys, etc) live inside the encrypted config payload
-- on the upstream side — never duplicated here.

create table if not exists public.aio_streams_links (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1 check (profile_id >= 1),
  aio_uuid text not null unique,
  enabled boolean not null default false,
  manifest_url text,
  config_password text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, profile_id)
);

create index if not exists idx_aio_streams_links_aio_uuid
  on public.aio_streams_links (aio_uuid);

create index if not exists idx_aio_streams_links_user_profile
  on public.aio_streams_links (user_id, profile_id);

comment on column public.aio_streams_links.config_password is
  'Secret used to authenticate subsequent GET/PUT/DELETE calls against the upstream AIOStreams instance. Never shared cross-user.';

-- updated_at trigger ----------------------------------------------------------

create or replace function public.touch_aio_streams_links_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_aio_streams_links_updated_at on public.aio_streams_links;

create trigger trg_aio_streams_links_updated_at
  before update on public.aio_streams_links
  for each row execute function public.touch_aio_streams_links_updated_at();

-- RLS -------------------------------------------------------------------------

alter table public.aio_streams_links enable row level security;

drop policy if exists aio_streams_select_own on public.aio_streams_links;

create policy aio_streams_select_own on public.aio_streams_links
  for select using (auth.uid() = user_id);

drop policy if exists aio_streams_insert_own on public.aio_streams_links;

create policy aio_streams_insert_own on public.aio_streams_links
  for insert with check (auth.uid() = user_id);

drop policy if exists aio_streams_update_own on public.aio_streams_links;

create policy aio_streams_update_own on public.aio_streams_links
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists aio_streams_delete_own on public.aio_streams_links;

create policy aio_streams_delete_own on public.aio_streams_links
  for delete using (auth.uid() = user_id);

grant select, insert, update, delete on public.aio_streams_links to authenticated;

-- Per-profile cleanup ---------------------------------------------------------
-- Re-replace public.sync_delete_profile_data so deleting a non-primary profile
-- also drops its AIOStreams link row, mirroring what 010 added for
-- aio_metadata_links. This rewrites the function whole — keep it in sync with
-- the body in migration 010 (and any later migrations that touched it).

create or replace function public.sync_delete_profile_data(p_profile_id integer)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_user_id uuid;
begin
  owner_user_id := public.current_sync_owner_id();

  delete from public.addons          where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.plugins         where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.watch_progress  where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.library_items   where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.watched_items   where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.profile_settings where user_id = owner_user_id and profile_id = p_profile_id;

  if p_profile_id <> 1 then
    delete from public.aio_metadata_links where user_id = owner_user_id and profile_id = p_profile_id;
    delete from public.aio_streams_links  where user_id = owner_user_id and profile_id = p_profile_id;
    delete from public.profiles           where user_id = owner_user_id and profile_index = p_profile_id;
  else
    update public.profiles
       set pin_hash = null,
           pin_failed_attempts = 0,
           pin_locked_until = null,
           updated_at = now()
     where user_id = owner_user_id and profile_index = p_profile_id;
  end if;
end;
$$
