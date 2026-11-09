package com.breviare.clicks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumer side of the click pipeline: reads batches from the Redis stream, writes them to Postgres
 * in one transaction, then acknowledges and deletes them.
 *
 * Delivery is at-least-once. If the app dies after the commit but before the ack, the batch is read
 * again on the next start (it is still pending for this consumer) and a few events can be inserted
 * twice. A batch that fails to write stays pending and is retried; it is never acknowledged first.
 *
 * Assumes a single app instance (one consumer name). Entries delivered to a different consumer name
 * that never came back would need XAUTOCLAIM, which is deliberately not implemented here.
 */
@Component
@ConditionalOnProperty(name = "breviare.clicks.queue.enabled", havingValue = "true")
public class ClickFlusher {

    private static final Logger log = LoggerFactory.getLogger(ClickFlusher.class);
    // Cap per tick so one burst cannot monopolise the (single-threaded) scheduler used by the nightly jobs.
    private static final int MAX_BATCHES_PER_TICK = 20;

    private final StringRedisTemplate redis;
    private final ClickBatchWriter writer;
    private final String stream;
    private final String group;
    private final Consumer consumer;
    private final int batchSize;

    private boolean groupReady = false;
    private boolean recoverPending = true; // on start, first finish whatever a previous run left pending

    public ClickFlusher(StringRedisTemplate redis, ClickBatchWriter writer,
                        @Value("${breviare.clicks.queue.stream}") String stream,
                        @Value("${breviare.clicks.queue.group}") String group,
                        @Value("${breviare.clicks.queue.consumer}") String consumer,
                        @Value("${breviare.clicks.queue.batch-size}") int batchSize) {
        this.redis = redis;
        this.writer = writer;
        this.stream = stream;
        this.group = group;
        this.consumer = Consumer.from(group, consumer);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${breviare.clicks.queue.flush-interval-ms}")
    public void flush() {
        try {
            ensureGroup();
            if (recoverPending) {
                while (readAndWrite(ReadOffset.from("0")) > 0) { /* keep draining own pending entries */ }
                recoverPending = false;
            }
            for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
                if (readAndWrite(ReadOffset.lastConsumed()) < batchSize) break;
            }
        } catch (Exception e) {
            // Redis or Postgres hiccup. Nothing was acknowledged, so nothing is lost; retry next tick,
            // re-reading our pending entries first.
            recoverPending = true;
            // Redis may have restarted without persistence and lost the consumer group (NOGROUP);
            // recreate it (idempotent) on the next tick rather than failing forever.
            groupReady = false;
            log.warn("Click flush failed, will retry: {}", e.toString());
        }
    }

    private int readAndWrite(ReadOffset offset) {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                consumer, StreamReadOptions.empty().count(batchSize), StreamOffset.create(stream, offset));
        if (records == null || records.isEmpty()) return 0;

        List<ClickEvent> events = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> r : records) {
            try {
                events.add(ClickEvent.fromFields(r.getValue()));
            } catch (Exception e) {
                // Malformed entry: retrying can never succeed, so drop it rather than loop forever.
                log.warn("Dropping malformed click record {}: {}", r.getId(), e.toString());
            }
        }

        if (!events.isEmpty()) writer.write(events); // throws on failure: records stay pending

        RecordId[] ids = records.stream().map(MapRecord::getId).toArray(RecordId[]::new);
        redis.opsForStream().acknowledge(stream, group, ids);
        redis.opsForStream().delete(stream, ids); // acked entries would otherwise sit in the stream until trimmed
        return records.size();
    }

    private void ensureGroup() {
        if (groupReady) return;
        try {
            // Start at 0 so clicks queued before the group first existed are not skipped.
            redis.opsForStream().createGroup(stream, ReadOffset.from("0"), group);
        } catch (Exception e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")
                    && !String.valueOf(e.getCause()).contains("BUSYGROUP")) throw e;
        }
        groupReady = true;
    }
}
