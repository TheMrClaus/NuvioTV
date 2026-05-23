-- Migration: optimistic-concurrency overload for sync_push_plugins.
--
-- Mirrors 016 (profiles) and 017 (addons). The 2-arg form from 001 stays
-- unchanged; the new 3-arg overload locks the profile's plugin rows, compares
-- max(updated_at), raises plugins_conflict (P0001) on stale snapshot, then
-- delegates to the existing 2-arg implementation.

create or replace function public.sync_push_plugins(
  p_plugins jsonb,
  p_profile_id integer,
  p_expected_updated_at timestamptz
)
returns timestamptz
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_user_id uuid;
  current_max_updated_at timestamptz;
  new_max_updated_at timestamptz;
begin
  owner_user_id := public.current_sync_owner_id();

  if p_expected_updated_at is not null then
    select max(updated_at) into current_max_updated_at
      from public.plugins
     where user_id = owner_user_id
       and profile_id = p_profile_id
     for update;

    if current_max_updated_at is not null
       and current_max_updated_at > p_expected_updated_at then
      raise exception 'plugins_conflict' using errcode = 'P0001';
    end if;
  end if;

  perform public.sync_push_plugins(p_plugins, p_profile_id);

  select max(updated_at) into new_max_updated_at
    from public.plugins
   where user_id = owner_user_id
     and profile_id = p_profile_id;

  return new_max_updated_at;
end;
$$;
