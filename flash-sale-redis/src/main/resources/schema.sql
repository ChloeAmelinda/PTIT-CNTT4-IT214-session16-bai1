CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(50) PRIMARY KEY,
    price INT NOT NULL,
    CONSTRAINT price_nonnegative CHECK (price >= 0)
);
