-- Run ONCE in pgAdmin against a BACKED-UP existing BusKaroo PostgreSQL database.
-- Idempotent for the newly added objects. This does NOT delete old reservations.
-- Requires public.reservation(id, user_id, status), and its status to become CONFIRMED on payment.
BEGIN;

CREATE TABLE IF NOT EXISTS coupon (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    eligibility_type VARCHAR(32) NOT NULL,
    percent_off NUMERIC(5,2) NOT NULL CHECK (percent_off > 0 AND percent_off <= 100),
    max_discount NUMERIC(10,2) NOT NULL CHECK (max_discount >= 0),
    min_fare NUMERIC(10,2) NOT NULL CHECK (min_fare >= 0),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from DATE,
    valid_until DATE,
    travel_from DATE,
    travel_until DATE,
    CONSTRAINT chk_coupon_booking_dates CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until),
    CONSTRAINT chk_coupon_travel_dates CHECK (travel_from IS NULL OR travel_until IS NULL OR travel_from <= travel_until)
);

ALTER TABLE reservation
    ADD COLUMN IF NOT EXISTS original_amount NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(32);

-- Preserve historical fares: they were not discounted, so their old total is their original total.
UPDATE reservation SET original_amount = total_amount WHERE original_amount IS NULL;

-- Unique historical redemption ledger. Written ONLY upon confirmation, never at PENDING.
CREATE TABLE IF NOT EXISTS coupon_redemption (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_code VARCHAR(32) NOT NULL REFERENCES coupon(code),
    reservation_id BIGINT NOT NULL UNIQUE REFERENCES reservation(id),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_redemption_user_coupon UNIQUE (user_id, coupon_code)
);
CREATE INDEX IF NOT EXISTS idx_coupon_redemption_user ON coupon_redemption(user_id);
CREATE INDEX IF NOT EXISTS idx_reservation_coupon_code ON reservation(coupon_code);

-- Demo catalog mirrors the six carousel offers. Configure promo dates before activation.
INSERT INTO coupon
(code, title, eligibility_type, percent_off, max_discount, min_fare,
 active, valid_from, valid_until, travel_from, travel_until)
VALUES
('FIRST25', 'First BusKaroo Journey', 'FIRST_BOOKING', 25.00, 250.00, 499.00, TRUE, NULL, NULL, NULL, NULL),
('CHHATH40', 'Chhath Puja Special', 'FESTIVAL', 40.00, 400.00, 799.00, FALSE, NULL, NULL, NULL, NULL),
('MONTH10', 'Monthly Saver', 'MONTHLY', 10.00, 100.00, 399.00, FALSE, NULL, NULL, NULL, NULL),
('WEEKEND10', 'Weekend Getaway', 'WEEKEND', 10.00, 120.00, 499.00, TRUE, NULL, NULL, NULL, NULL),
('SLEEP10', 'Sleeper Bus Special', 'SLEEPER', 10.00, 150.00, 599.00, TRUE, NULL, NULL, NULL, NULL),
('BACK10', 'Welcome Back', 'RETURNING', 10.00, 125.00, 499.00, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (code) DO NOTHING;

-- PostgreSQL confirmation trigger: atomic one-redemption-per-user-per-code guarantee.
-- Keeps a permanently redeemed code used even if a paid booking is later cancelled.
-- The advisory lock serializes concurrent confirmations per user.
CREATE OR REPLACE FUNCTION buskaroo_record_coupon_on_confirmation()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status::text = 'CONFIRMED'
       AND OLD.status::text IS DISTINCT FROM 'CONFIRMED' THEN
        PERFORM pg_advisory_xact_lock(NEW.user_id);

        IF NEW.coupon_code IS NOT NULL AND TRIM(NEW.coupon_code) <> '' THEN
            IF NOT EXISTS (SELECT 1 FROM coupon c WHERE c.code = NEW.coupon_code AND c.active) THEN
                RAISE EXCEPTION 'Coupon % is not active', NEW.coupon_code
                    USING ERRCODE = '23514';
            END IF;

            IF NEW.coupon_code = 'FIRST25' AND EXISTS (
                SELECT 1 FROM reservation r
                WHERE r.user_id = NEW.user_id AND r.id <> NEW.id AND r.status::text = 'CONFIRMED'
            ) THEN
                RAISE EXCEPTION 'FIRST25 requires a first confirmed booking'
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1 FROM coupon_redemption cr
                WHERE cr.user_id = NEW.user_id AND cr.coupon_code = NEW.coupon_code
            ) THEN
                RAISE EXCEPTION 'Coupon % has already been used by this customer', NEW.coupon_code
                    USING ERRCODE = '23505';
            END IF;

            INSERT INTO coupon_redemption (user_id, coupon_code, reservation_id)
            VALUES (NEW.user_id, NEW.coupon_code, NEW.id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_buskaroo_coupon_on_confirm ON reservation;
CREATE TRIGGER trg_buskaroo_coupon_on_confirm
AFTER UPDATE OF status ON reservation
FOR EACH ROW
EXECUTE FUNCTION buskaroo_record_coupon_on_confirmation();

COMMIT;
