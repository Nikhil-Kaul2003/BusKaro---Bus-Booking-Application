-- Run AFTER V1__buskaroo_coupons.sql has been executed successfully.
-- IMPORTANT: Create this as a NEW SQL file. Do not replace or rerun your old schema.sql or data.sql.
-- This patch upgrades coupon confirmation to use successful payment history.
-- It does not delete any reservations, payments, or coupons.
BEGIN;

CREATE OR REPLACE FUNCTION buskaroo_record_coupon_on_confirmation()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status::text = 'CONFIRMED'
       AND OLD.status::text IS DISTINCT FROM 'CONFIRMED' THEN
        -- Same user cannot confirm two coupon reservations in parallel.
        PERFORM pg_advisory_xact_lock(NEW.user_id);

        IF NEW.coupon_code IS NOT NULL AND BTRIM(NEW.coupon_code) <> '' THEN
            IF NOT EXISTS (
                SELECT 1 FROM coupon c
                WHERE c.code = NEW.coupon_code AND c.active
            ) THEN
                RAISE EXCEPTION 'Coupon % is not active', NEW.coupon_code
                    USING ERRCODE = '23514';
            END IF;

            -- A COMPLETED payment still counts even if the paid reservation was
            -- subsequently CANCELLED. Exclude the payment being confirmed now.
            IF NEW.coupon_code = 'FIRST25' AND (
                EXISTS (
                    SELECT 1 FROM payment p
                    WHERE p.user_id = NEW.user_id
                      AND p.reservation_id <> NEW.id
                      AND p.status::text = 'COMPLETED'
                )
                OR EXISTS (
                    SELECT 1 FROM reservation r
                    WHERE r.user_id = NEW.user_id
                      AND r.id <> NEW.id
                      AND r.status::text = 'CONFIRMED'
                )
            ) THEN
                RAISE EXCEPTION 'FIRST25 requires a first successfully paid booking'
                    USING ERRCODE = '23514';
            END IF;

            IF NEW.coupon_code = 'BACK10' AND NOT (
                EXISTS (
                    SELECT 1 FROM payment p
                    WHERE p.user_id = NEW.user_id
                      AND p.reservation_id <> NEW.id
                      AND p.status::text = 'COMPLETED'
                )
                OR EXISTS (
                    SELECT 1 FROM reservation r
                    WHERE r.user_id = NEW.user_id
                      AND r.id <> NEW.id
                      AND r.status::text = 'CONFIRMED'
                )
            ) THEN
                RAISE EXCEPTION 'BACK10 requires a previously paid booking'
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1 FROM coupon_redemption cr
                WHERE cr.user_id = NEW.user_id
                  AND cr.coupon_code = NEW.coupon_code
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

-- The trigger from V1 already calls the function, so no DROP or CREATE TRIGGER is needed.
COMMIT;
