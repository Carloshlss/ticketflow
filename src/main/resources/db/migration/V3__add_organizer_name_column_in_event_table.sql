ALTER TABLE event ADD COLUMN organizer_name VARCHAR(50);        -- 1. nullable
UPDATE event SET organizer_name = 'Unknown Organizer';          -- 2. backfill
ALTER TABLE event ALTER COLUMN organizer_name SET NOT NULL;     -- 3. constraint