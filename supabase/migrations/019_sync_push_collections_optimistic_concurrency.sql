-- Migration: optimistic-concurrency overload for sync_push_collections.
--
-- Mirrors 016/017/018. The 2-arg form from 007 stays unchanged; the new 3-arg
-- overload locks the profile's single collections row (unique on user_id +
-- profile_id), compares its updated_at, raises collections_conflict (P0001)
-- on stale snapshot, then delegates.

create or replace function public.sync_push_collections(
  p_profile_id integer,
  p_collections_json jsonb,
  p_expected_updated_at timestamptz
)
returns timestamptz
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_user_id uuid;
  current_updated_at timestamptz;
  new_updated_at timestamptz;
begin
  owner_user_id := public.current_sync_owner_id();

  if p_expected_updated_at is not null then
    select updated_at into current_updated_at
      from public.collections
     where user_id = owner_user_id
       and profile_id = p_profile_id
     for update;

    if current_updated_at is not null
       and current_updated_at > p_expected_updated_at then
      raise exception 'collections_conflict' using errcode = 'P0001';
    end if;
  end if;

  perform public.sync_push_collections(p_profile_id, p_collections_json);

  select updated_at into new_updated_at
    from public.collections
   where user_id = owner_user_id
     and profile_id = p_profile_id;

  return new_updated_at;
end;
$$;
