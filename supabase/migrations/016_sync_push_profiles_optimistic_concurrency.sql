-- Migration: optimistic-concurrency overload for sync_push_profiles.
--
-- The original sync_push_profiles(p_profiles jsonb) (last redefined in 011)
-- is unchanged — older TV builds keep calling it. This migration adds a new
-- 2-arg overload that the panel uses to detect concurrent writes.
--
-- Pattern matches 008's sync_push_profile_settings_partial:
--   * caller passes the max(updated_at) it saw on read as p_expected_updated_at
--   * if any of the owner's profile rows have updated_at newer than that,
--     raise profiles_conflict (errcode P0001) so the caller can refetch + retry
--   * otherwise delegate to the existing 1-arg implementation and return the
--     new max(updated_at) so the caller can advance its snapshot without an
--     additional fetch.
--
-- Profile rows are FOR UPDATE locked during the check to serialize concurrent
-- 2-arg pushes against each other.

create or replace function public.sync_push_profiles(
  p_profiles jsonb,
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
      from public.profiles
     where user_id = owner_user_id
     for update;

    if current_max_updated_at is not null
       and current_max_updated_at > p_expected_updated_at then
      raise exception 'profiles_conflict' using errcode = 'P0001';
    end if;
  end if;

  perform public.sync_push_profiles(p_profiles);

  select max(updated_at) into new_max_updated_at
    from public.profiles
   where user_id = owner_user_id;

  return new_max_updated_at;
end;
$$;
