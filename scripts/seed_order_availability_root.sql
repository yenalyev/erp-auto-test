-- REQ-ORD: scope for order gathering / availability (staging/dev).
-- Root = tyolki storage (id=10). Candidates = root + descendants ∩ STORAGE|PRODUCTION ∩ active.
-- Unique global row: (name, username IS NULL).

UPDATE app_config
SET value = '[{"name": "storageId", "values": ["10"]}]'::jsonb
WHERE name = 'order_availability_root_storage'
  AND username IS NULL;

INSERT INTO app_config (name, value, username)
SELECT 'order_availability_root_storage',
       '[{"name": "storageId", "values": ["10"]}]'::jsonb,
       NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM app_config
    WHERE name = 'order_availability_root_storage'
      AND username IS NULL
);

-- CPMA-711: gathering candidates require is_order_hub = true
UPDATE storage
SET is_order_hub = true
WHERE id = 10
  AND is_order_hub = false;

SELECT name, value
FROM app_config
WHERE name = 'order_availability_root_storage'
  AND username IS NULL;

SELECT id, name, is_order_hub
FROM storage
WHERE id = 10;
