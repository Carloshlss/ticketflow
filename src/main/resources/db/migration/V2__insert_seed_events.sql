-- [SEED] Dados iniciais para desenvolvimento e testes manuais.
INSERT INTO event (name, description, venue, city, starts_at, ends_at, ticket_price, total_tickets,
                   available_tickets, status, created_at, updated_at, version)
VALUES
    ('Rock in Sampa 2026', 'Biggest rock festival in Brazil', 'Allianz Parque',
     'São Paulo', '2026-11-20 18:00:00-03', '2026-11-21 02:00:00-03',
     450.00, 5000, 5000, 'PUBLISHED', NOW(), NOW(), 0),
    ('Java Summit Brasil', 'Conference about the Java ecosystem', 'Expo Center Norte',
     'São Paulo', '2026-09-12 9:00:00-03', '2026-09-15 18:00:00-03',
     320.00, 800, 800, 'PUBLISHED', NOW(), NOW(), 0),
    ('Indie Night', 'Independent bands showcase', 'Circo Voador',
     'Rio de Janeiro', '2026-10-05 21:00:00-03', '2026-10-06 01:00:00-03',
     90.00, 1200, 1200, 'DRAFT', NOW(), NOW(), 0);