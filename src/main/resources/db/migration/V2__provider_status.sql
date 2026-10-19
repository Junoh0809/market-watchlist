CREATE TABLE provider_status (
    provider VARCHAR(64) NOT NULL,
    market VARCHAR(16) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE,
    source_timestamp TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(256),
    PRIMARY KEY (provider, market, symbol)
);
