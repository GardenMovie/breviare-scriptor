package com.breviare.clicks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Producer side of the click pipeline: one XADD per redirect, on the request thread.
 *
 * Only exists when breviare.clicks.queue.enabled=true. offer() never throws; it returns false when
 * the event could not be queued (Redis down, timeout, memory limit) and the caller then writes to
 * Postgres directly, exactly as it does with the queue off. A Redis outage therefore costs speed,
 * never clicks.
 */
@Component
@ConditionalOnProperty(name = "breviare.clicks.queue.enabled", havingValue = "true")
public class ClickQueue {

    private static final Logger log = LoggerFactory.getLogger(ClickQueue.class);

    private final StringRedisTemplate redis;
    private final byte[] streamKey;
    private final XAddOptions options;

    public ClickQueue(StringRedisTemplate redis,
                      @Value("${breviare.clicks.queue.stream}") String stream,
                      @Value("${breviare.clicks.queue.max-length}") long maxLength) {
        this.redis = redis;
        this.streamKey = stream.getBytes(UTF_8);
        // Approximate trimming is O(1) amortised and keeps the stream bounded even if the consumer is down.
        this.options = XAddOptions.maxlen(maxLength).approximateTrimming(true);
    }

    public boolean offer(ClickEvent event) {
        try {
            Map<byte[], byte[]> fields = new LinkedHashMap<>();
            event.toFields().forEach((k, v) -> fields.put(k.getBytes(UTF_8), v.getBytes(UTF_8)));
            ByteRecord record = StreamRecords.rawBytes(fields).withStreamKey(streamKey);
            // Connection-level call: StreamOperations in this Spring Data Redis version cannot pass MAXLEN.
            redis.execute((RedisCallback<RecordId>) con -> con.streamCommands().xAdd(record, options));
            return true;
        } catch (Exception e) {
            // Debug on purpose: with Redis down this fires on every redirect.
            log.debug("Click enqueue failed, writing directly to Postgres: {}", e.toString());
            return false;
        }
    }
}
