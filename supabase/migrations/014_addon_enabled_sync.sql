-- 014_addon_enabled_sync.sql
-- Extend sync_push_addons to thread the per-addon `enabled` flag through to
-- public.addons (the column already exists with default true; only the RPC
-- needed updating to mirror the sync_push_plugins pattern).

create or replace function public.sync_push_addons(
  p_addons jsonb,
  p_profile_id integer
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_user_id uuid;
begin
  owner_user_id := public.current_sync_owner_id();

  delete from public.addons
   where user_id = owner_user_id
     and profile_id = p_profile_id;

  insert into public.addons (user_id, url, name, enabled, sort_order, profile_id)
  select
    owner_user_id,
    item->>'url',
    nullif(item->>'name', ''),
    coalesce((item->>'enabled')::boolean, true),
    coalesce((item->>'sort_order')::integer, 0),
    p_profile_id
  from jsonb_array_elements(coalesce(p_addons, '[]'::jsonb)) item
  where coalesce(item->>'url', '') <> '';
end;
$$;
