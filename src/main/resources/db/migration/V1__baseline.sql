-- 기준: gopssl-backend(FastAPI)의 SQLAlchemy 모델 (models/*.py).
-- create_all이 만드는 스키마와 같도록 옮겼다. 인덱스 이름(ix_*)도 동일하다.
-- is_deleted, display_order, customers.name의 기본값은 모델에서 Python 쪽 default라 DB DEFAULT가 없다.
-- 운영 적용 전 실제 스키마와 대조가 필요하다.

CREATE TABLE users (
    id             SERIAL PRIMARY KEY,
    name           VARCHAR(50),
    email          VARCHAR(100) NOT NULL UNIQUE,
    password       TEXT NOT NULL,
    store_name     VARCHAR(100),
    address        TEXT,
    address_detail TEXT,
    zip_code       TEXT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE INDEX ix_users_id ON users (id);

CREATE TABLE customers (
    id           SERIAL PRIMARY KEY,
    user_id      INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(50),
    phone_number VARCHAR(20),
    detail       TEXT,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_deleted   BOOLEAN NOT NULL,
    deleted_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_customers_id ON customers (id);

CREATE TABLE treatment_categories (
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR NOT NULL,
    display_order INTEGER NOT NULL
);
CREATE INDEX ix_treatment_categories_id ON treatment_categories (id);

CREATE TABLE treatments (
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR NOT NULL,
    default_price INTEGER NOT NULL,
    category_id   INTEGER REFERENCES treatment_categories (id),
    display_order INTEGER NOT NULL
);

CREATE TABLE sales (
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    customer_id   INTEGER REFERENCES customers (id) ON DELETE SET NULL,
    sale_date     DATE NOT NULL,
    detail        TEXT,
    payment_type  VARCHAR(20),
    total_amount  INTEGER,
    num_of_visits INTEGER,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
    extra_prices  JSON
);
CREATE INDEX ix_sales_id ON sales (id);

CREATE TABLE sale_treatments (
    id           SERIAL PRIMARY KEY,
    sale_id      INTEGER NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    treatment_id INTEGER REFERENCES treatments (id) ON DELETE SET NULL,
    price        INTEGER NOT NULL
);
CREATE INDEX ix_sale_treatments_id ON sale_treatments (id);
