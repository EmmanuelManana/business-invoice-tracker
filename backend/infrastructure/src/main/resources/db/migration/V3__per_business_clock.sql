-- The demo time machine is per business: each business has its own simulated offset from real time.
ALTER TABLE businesses
    ADD COLUMN clock_offset_days integer NOT NULL DEFAULT 0
        CHECK (clock_offset_days BETWEEN 0 AND 3650);
