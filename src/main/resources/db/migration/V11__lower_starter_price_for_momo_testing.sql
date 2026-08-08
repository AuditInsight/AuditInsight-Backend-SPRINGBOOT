
-- Temporary low test price so a real MoMo sandbox/production run costs ~100 RWF instead of
-- the real placeholder price. Must be reverted to the real STARTER price before launch.
UPDATE plans SET monthly_price_usd = 0.07 WHERE tier = 'STARTER';