-- =====================================================================
-- Sample master data: procurement centres and crops.
-- Centres and rates are sample values for the demo, not official data.
-- =====================================================================

-- Capacity per slot = counters x slot_minutes / avg_service_min
--   TNJ: 3 x 30 / 10 = 9 farmers per slot
--   TVR: 2 x 30 / 10 = 6 farmers per slot
--   NGT: 2 x 30 / 12 = 5 farmers per slot
INSERT INTO centres (code, name, district, address, open_time, close_time, slot_minutes, counters, avg_service_min)
VALUES ('TNJ', 'Thanjavur Procurement Centre',    'Thanjavur',    'Near New Bus Stand, Thanjavur 613005',   '08:00', '17:00', 30, 3, 10),
       ('TVR', 'Tiruvarur Procurement Centre',    'Tiruvarur',    'Near Collectorate, Tiruvarur 610004',    '08:00', '17:00', 30, 2, 10),
       ('NGT', 'Nagapattinam Procurement Centre', 'Nagapattinam', 'Near Old Bus Stand, Nagapattinam 611001', '08:00', '16:00', 30, 2, 12);

INSERT INTO crops (name_en, name_ta, name_hi, rate_per_qtl, max_moisture_pct)
VALUES ('Paddy (Common)',  'நெல் (பொது ரகம்)',  'धान (सामान्य)',  2369.00, 17.0),
       ('Paddy (Grade A)', 'நெல் (சன்ன ரகம்)', 'धान (ग्रेड ए)',  2389.00, 17.0),
       ('Maize',           'மக்காச்சோளம்',      'मक्का',          2400.00, 14.0),
       ('Ragi',            'கேழ்வரகு',          'रागी',           4886.00, 12.0);
