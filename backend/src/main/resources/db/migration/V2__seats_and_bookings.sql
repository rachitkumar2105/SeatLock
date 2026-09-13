CREATE TABLE seats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    section         VARCHAR(50) NOT NULL,
    row_label       VARCHAR(10) NOT NULL,
    number          INTEGER NOT NULL,
    price           NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'LOCKED', 'BOOKED')),
    locked_by       UUID,
    lock_expires_at TIMESTAMP,
    version         INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX ix_seats_event_id_status ON seats (event_id, status);
CREATE UNIQUE INDEX ux_seats_event_section_row_number ON seats (event_id, section, row_label, number);

CREATE TABLE bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id),
    event_id        UUID NOT NULL REFERENCES events (id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    total_amount    NUMERIC(10, 2) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_bookings_idempotency_key ON bookings (idempotency_key);
CREATE INDEX ix_bookings_user_id ON bookings (user_id);

CREATE TABLE booking_seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id     UUID NOT NULL REFERENCES seats (id)
);

CREATE UNIQUE INDEX ux_booking_seats_seat_id ON booking_seats (seat_id);
CREATE INDEX ix_booking_seats_booking_id ON booking_seats (booking_id);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings (id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    amount          NUMERIC(10, 2) NOT NULL,
    provider_ref    VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX ix_payments_booking_id ON payments (booking_id);
