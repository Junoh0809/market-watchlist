package com.stockmanagement.market;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Collects once per symbol per minute, then shares the last successful quote across users. */
@Service
public class QuoteService {
    private static final int FRESHNESS_MINUTES = 2;

    private final QuoteProvider provider;
    private final QuoteAttemptRecorder recorder;
    private final Clock clock;
    private final ConcurrentMap<String, State> quotes = new ConcurrentHashMap<>();

    @Autowired
    public QuoteService(QuoteProvider provider, QuoteAttemptRecorder recorder) {
        this(provider, recorder, Clock.systemUTC());
    }

    QuoteService(QuoteProvider provider, Clock clock) { this(provider, QuoteAttemptRecorder.NOOP, clock); }

    QuoteService(QuoteProvider provider, QuoteAttemptRecorder recorder, Clock clock) {
        this.provider = provider;
        this.recorder = recorder;
        this.clock = clock;
    }

    public QuoteResult quote(SecurityCatalog.Security security) {
        Instant now = clock.instant();
        Instant bucket = now.truncatedTo(ChronoUnit.MINUTES);
        String key = security.market() + ":" + security.symbol();
        State state = quotes.compute(key, (ignored, previous) -> collect(security, bucket, now, previous));
        if (state.lastSuccess() == null) return new QuoteResult(null, provider.name(), QuoteStatus.UPDATING);
        if (state.collectionStatus() == QuoteStatus.UPDATING)
            return new QuoteResult(state.lastSuccess(), provider.name(), QuoteStatus.UPDATING);
        boolean stale = state.collectionStatus() == QuoteStatus.LAST_KNOWN
                || now.isAfter(state.lastSuccess().sourceTimestamp().plus(FRESHNESS_MINUTES, ChronoUnit.MINUTES));
        return new QuoteResult(state.lastSuccess(), provider.name(), stale ? QuoteStatus.LAST_KNOWN : QuoteStatus.NORMAL);
    }

    private State collect(SecurityCatalog.Security security, Instant bucket, Instant now, State previous) {
        if (previous != null && !bucket.isAfter(previous.attemptedBucket())) return previous;
        QuoteProvider.QuoteSnapshot last = previous == null ? null : previous.lastSuccess();
        try {
            QuoteProvider.QuoteSnapshot fetched = provider.fetch(security, now);
            if (last != null && fetched.sourceTimestamp().isBefore(last.sourceTimestamp())) {
                recorder.failure(provider.name(), security, now, QuoteStatus.LAST_KNOWN,
                        "Source timestamp moved backward");
                return new State(bucket, last, QuoteStatus.LAST_KNOWN);
            }
            QuoteStatus status = now.isAfter(fetched.sourceTimestamp().plus(FRESHNESS_MINUTES, ChronoUnit.MINUTES))
                    ? QuoteStatus.LAST_KNOWN : QuoteStatus.NORMAL;
            recorder.success(provider.name(), security, now, fetched.sourceTimestamp(), status);
            return new State(bucket, fetched, status);
        } catch (QuoteProvider.Updating ignored) {
            recorder.updating(provider.name(), security, now);
            return new State(bucket, last, QuoteStatus.UPDATING);
        } catch (QuoteProvider.Unavailable ignored) {
            QuoteStatus status = last == null ? QuoteStatus.UPDATING : QuoteStatus.LAST_KNOWN;
            recorder.failure(provider.name(), security, now, status, "Provider unavailable");
            return new State(bucket, last, status);
        }
    }

    private record State(Instant attemptedBucket, QuoteProvider.QuoteSnapshot lastSuccess,
                         QuoteStatus collectionStatus) { }

    public enum QuoteStatus { NORMAL, LAST_KNOWN, UPDATING }

    public record QuoteResult(QuoteProvider.QuoteSnapshot snapshot, String source, QuoteStatus status) { }
}
