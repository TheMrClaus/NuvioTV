-- Migration: optimistic-concurrency overload for sync_push_addons.
--
-- Pattern matches 016 (profiles concurrency) and 008 (profile_settings partial
-- merge). The 2-arg form from 014 stays unchanged; the new 3-arg overload lets
-- the panel pass max(updated_at) and raises addons_conflict (P0001) on stale
-- snapshots.
--
-- Addons are full-replaced (delete-then-insert) per profile inside the 2-arg
-- implementation, so the concurrency check compares max(updated_at) across all
-- of the owner's rows for the target profile_id before delegating.

create or replace function public.sync_push_addons(
  p_addons jsonb,
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
      from public.addons
     where user_id = owner_user_id
       and profile_id = p_profile_id
     for update;

    if current_max_updated_at is not null
       and current_max_updated_at > p_expected_updated_at then
      raise exception 'addons_conflict' using errcode = 'P0001';
    end if;
  end if;

  perform public.sync_push_addons(p_addons, p_profile_id);

  select max(updated_at) into new_max_updated_at
    from public.addons
   where user_id = owner_user_id
     and profile_id = p_profile_id;

  return new_max_updated_at;
end;
$$;
