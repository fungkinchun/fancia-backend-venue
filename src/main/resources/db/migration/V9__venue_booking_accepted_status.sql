ALTER TABLE venue_bookings
    DROP CONSTRAINT IF EXISTS venue_bookings_status_check;

ALTER TABLE venue_bookings
    ADD CONSTRAINT venue_bookings_status_check CHECK (
        status IN (
            'REQUESTED',
            'PAID',
            'ACCEPTED',
            'DENIED',
            'WITHDRAWN',
            'EXPIRED'
        )
    );
