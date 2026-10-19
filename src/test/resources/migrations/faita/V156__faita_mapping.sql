ALTER TABLE faita.resource_alias_with_initiators ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE faita.resource_alias_with_initiators ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE faita.resource_alias_with_initiators ADD COLUMN IF NOT EXISTS created_by TEXT NOT NULL DEFAULT 'system';
ALTER TABLE faita.resource_alias_with_initiators ADD COLUMN IF NOT EXISTS updated_by TEXT NOT NULL DEFAULT 'system';
-- Never read: the view matches aliases by initiator id, and the Faita name lives in faita.initiators
ALTER TABLE faita.resource_alias_with_initiators DROP COLUMN IF EXISTS initiator_name;

ALTER TABLE faita.resource_alias_with_ammo ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE faita.resource_alias_with_ammo ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE faita.resource_alias_with_ammo ADD COLUMN IF NOT EXISTS created_by TEXT NOT NULL DEFAULT 'system';
ALTER TABLE faita.resource_alias_with_ammo ADD COLUMN IF NOT EXISTS updated_by TEXT NOT NULL DEFAULT 'system';
-- Never read: the view matches aliases by name, and the Faita name lives in faita.ammunition
ALTER TABLE faita.resource_alias_with_ammo DROP COLUMN IF EXISTS ammunition_name;

-- Aliases were entered by hand with explicit ids, which do not move the identity: the page would collide with them
SELECT setval(pg_get_serial_sequence('faita.resource_alias_with_ammo', 'id'), coalesce(max(id), 0) + 1, false)
FROM faita.resource_alias_with_ammo;
SELECT setval(pg_get_serial_sequence('faita.resource_alias_with_initiators', 'id'), coalesce(max(id), 0) + 1, false)
FROM faita.resource_alias_with_initiators;

CREATE TABLE IF NOT EXISTS faita.initiators (
    initiator_id int8 NOT NULL,
    initiator_name text NULL,
    initiator_full_name text NULL,
    status text NULL,
    description text NULL,
    unit_id int8 NULL,
    CONSTRAINT initiators_pkey PRIMARY KEY (initiator_id)
);

-- Unmapped Faita entries are found id by id through these instead of grouping every unmapped usage row.
-- The order matches "latest usage first" so the last usage of an id is a single index probe.
CREATE INDEX IF NOT EXISTS idx_faita_vw_ammo_usage_unmapped_ammo
    ON faita.vw_ammo_usage (ammo_id, event_date_time DESC NULLS LAST)
    WHERE resource_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_faita_vw_ammo_usage_unmapped_initiator
    ON faita.vw_ammo_usage (initiator_id, event_date_time DESC NULLS LAST)
    WHERE initiator_resource_id IS NULL;

