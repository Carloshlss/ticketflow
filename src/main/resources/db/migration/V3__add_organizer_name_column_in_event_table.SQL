ALTER TABLE event ADD COLUMN organizer_name VARCHAR(50);

UPDATE event SET organizer_name = 'King Events';