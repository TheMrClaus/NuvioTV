-- AIOMetadata panel support
--
-- Add provisioning state columns so account.omnio.tv can render the same
-- "ready / provisioning_failed / unknown" lifecycle the Source Cloud panel
-- shows. Mirrors public.source_cloud_configs (013_source_cloud.sql).
--
-- Existing rows with a config_password were provisioned successfully against
-- the upstream cedya77/aiometadata instance, so backfill those to 'ready'
-- to avoid a false "needs provisioning" state on first panel load.

alter table public.aio_metadata_links
  add column if not exists config_status text not null default 'unknown',
  add column if not exists last_provisioned_at timestamptz,
  add column if not exists last_validated_at timestamptz;

update public.aio_metadata_links
   set config_status = 'ready',
       last_validated_at = coalesce(last_validated_at, updated_at)
 where aio_uuid is not null
   and config_password is not null
   and config_password <> ''
   and config_status = 'unknown';
