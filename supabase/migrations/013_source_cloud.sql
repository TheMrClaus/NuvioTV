-- Omnio Source Cloud backend foundation.
--
-- AIOStreams is an internal implementation detail. These tables keep each
-- Omnio user/profile isolated and keep token/config-secret material out of
-- direct authenticated client access.
--
-- Config status values align with the Android SourceCloudConfig model:
--   unknown | not_provisioned | ready | provisioning_failed | unavailable | invalid
--
-- Service token status values align with the Android SourceCloudServiceToken model:
--   disconnected | connected | expired | error

create table if not exists public.source_cloud_configs (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1 check (profile_id >= 1),
  aiostreams_config_id text,
  aiostreams_config_secret_ciphertext text,
  aiostreams_config_secret_nonce text,
  config_status text not null default 'not_provisioned'
    check (config_status in ('unknown', 'not_provisioned', 'ready', 'provisioning_failed', 'unavailable', 'invalid')),
  advanced_config_url_hash text,
  advanced_config_url_expires_at timestamptz,
  last_provisioned_at timestamptz,
  last_validated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, profile_id)
);

create table if not exists public.source_cloud_service_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1 check (profile_id >= 1),
  service text not null check (service in ('real_debrid', 'torbox')),
  access_token_ciphertext text,
  refresh_token_ciphertext text,
  token_nonce text,
  token_expires_at timestamptz,
  status text not null default 'disconnected'
    check (status in ('disconnected', 'connected', 'expired', 'error')),
  label text,
  last_checked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, service)
);

create table if not exists public.source_cloud_settings (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1 check (profile_id >= 1),
  enabled boolean not null default true,
  preferred_quality text,
  max_size_bytes bigint check (max_size_bytes is null or max_size_bytes >= 0),
  cached_only boolean not null default true,
  hdr_preference text,
  preferred_language text,
  provider_toggles jsonb not null default '{}'::jsonb,
  settings_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, profile_id)
);

create table if not exists public.source_cloud_advanced_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1 check (profile_id >= 1),
  session_token_hash text not null unique,
  status text not null default 'active'
    check (status in ('active', 'used', 'expired', 'revoked')),
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_source_cloud_configs_user_profile
  on public.source_cloud_configs (user_id, profile_id);

create index if not exists idx_source_cloud_service_tokens_user_profile
  on public.source_cloud_service_tokens (user_id, profile_id);

create index if not exists idx_source_cloud_service_tokens_user_profile_service
  on public.source_cloud_service_tokens (user_id, profile_id, service);

create index if not exists idx_source_cloud_settings_user_profile
  on public.source_cloud_settings (user_id, profile_id);

create index if not exists idx_source_cloud_advanced_sessions_user_profile
  on public.source_cloud_advanced_sessions (user_id, profile_id);

create index if not exists idx_source_cloud_advanced_sessions_token_hash
  on public.source_cloud_advanced_sessions (session_token_hash);

create index if not exists idx_source_cloud_advanced_sessions_expires_at
  on public.source_cloud_advanced_sessions (expires_at);

drop trigger if exists trg_source_cloud_configs_updated_at on public.source_cloud_configs;
create trigger trg_source_cloud_configs_updated_at before update on public.source_cloud_configs
for each row execute function public.set_updated_at();

drop trigger if exists trg_source_cloud_service_tokens_updated_at on public.source_cloud_service_tokens;
create trigger trg_source_cloud_service_tokens_updated_at before update on public.source_cloud_service_tokens
for each row execute function public.set_updated_at();

drop trigger if exists trg_source_cloud_settings_updated_at on public.source_cloud_settings;
create trigger trg_source_cloud_settings_updated_at before update on public.source_cloud_settings
for each row execute function public.set_updated_at();

drop trigger if exists trg_source_cloud_advanced_sessions_updated_at on public.source_cloud_advanced_sessions;
create trigger trg_source_cloud_advanced_sessions_updated_at before update on public.source_cloud_advanced_sessions
for each row execute function public.set_updated_at();

alter table public.source_cloud_configs enable row level security;
alter table public.source_cloud_service_tokens enable row level security;
alter table public.source_cloud_settings enable row level security;
alter table public.source_cloud_advanced_sessions enable row level security;

-- source_cloud_settings: authenticated users may CRUD their own rows.
drop policy if exists source_cloud_settings_select_own on public.source_cloud_settings;
create policy source_cloud_settings_select_own on public.source_cloud_settings
  for select using (public.can_access_owner(user_id));

drop policy if exists source_cloud_settings_insert_own on public.source_cloud_settings;
create policy source_cloud_settings_insert_own on public.source_cloud_settings
  for insert with check (user_id = public.current_sync_owner_id());

drop policy if exists source_cloud_settings_update_own on public.source_cloud_settings;
create policy source_cloud_settings_update_own on public.source_cloud_settings
  for update using (public.can_access_owner(user_id))
  with check (user_id = public.current_sync_owner_id());

drop policy if exists source_cloud_settings_delete_own on public.source_cloud_settings;
create policy source_cloud_settings_delete_own on public.source_cloud_settings
  for delete using (user_id = public.current_sync_owner_id());

-- source_cloud_configs: holds AIOStreams config secrets. Authenticated role has
-- no direct privileges; Edge Functions use service role (bypasses RLS) to read
-- config_status and project non-secret fields back to clients.
revoke all on public.source_cloud_configs from authenticated;

-- source_cloud_service_tokens: holds encrypted debrid service tokens. Authenticated
-- role has no direct privileges; Edge Functions use service role to project
-- connection status (connected/disconnected/expired/error) without token material.
revoke all on public.source_cloud_service_tokens from authenticated;

-- source_cloud_advanced_sessions: short-lived session tokens for QR-based advanced
-- config access. Authenticated role has no direct privileges; sessions are created
-- and validated exclusively by Edge Functions using the service role.
revoke all on public.source_cloud_advanced_sessions from authenticated;

grant select, insert, update, delete on public.source_cloud_settings to authenticated;

comment on table public.source_cloud_configs is
  'Per-user/profile Source Cloud AIOStreams config metadata. Secret columns store encrypted ciphertext/nonce only and are accessed through service-role Edge Functions.';

comment on table public.source_cloud_service_tokens is
  'Per-user/profile encrypted source-service credentials. Direct authenticated access is revoked; Edge Functions project status without token material.';

comment on table public.source_cloud_settings is
  'Curated per-user/profile Source Cloud settings exposed by Omnio apps.';

comment on table public.source_cloud_advanced_sessions is
  'Short-lived QR/web sessions for advanced Source Cloud config access. Stores only SHA-256 hashes of random session tokens.';

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

  delete from public.addons where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.plugins where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.watch_progress where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.library_items where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.watched_items where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.profile_settings where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.source_cloud_configs where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.source_cloud_service_tokens where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.source_cloud_settings where user_id = owner_user_id and profile_id = p_profile_id;
  delete from public.source_cloud_advanced_sessions where user_id = owner_user_id and profile_id = p_profile_id;

  if p_profile_id <> 1 then
    delete from public.aio_metadata_links
       where user_id = owner_user_id and profile_id = p_profile_id;
    delete from public.profiles where user_id = owner_user_id and profile_index = p_profile_id;
  else
    update public.profiles
       set pin_hash = null,
           pin_failed_attempts = 0,
           pin_locked_until = null,
           updated_at = now()
     where user_id = owner_user_id and profile_index = p_profile_id;
  end if;
end;
$$;
