/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jun.mqttx.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提供基于时间戳的 UUID.
 * <p>
 * copy from Cassandra Uuids.
 *
 * @author Jun
 * @since 1.2.0
 */
public class Uuids {

    /**
     * UUID v1 timestamps must be expressed relatively to October 15th, 1582 – the day when Gregorian
     * calendar was introduced. This constant captures that moment in time expressed in milliseconds
     * before the Unix epoch. It can be obtained by calling:
     *
     * <pre>
     *   Instant.parse("1582-10-15T00:00:00Z").toEpochMilli();
     * </pre>
     */
    private static final long START_EPOCH_MILLIS = -12219292800000L;

    private static final ClockSeqAndNodeContainer CLOCK_SEQ_AND_NODE = new ClockSeqAndNodeContainer();
    private static final AtomicLong lastTimestamp = new AtomicLong(0L);

    /**
     * 生成基于时间戳的 uuid
     *
     * @return {@link UUID}
     */
    public static String timeBased() {
        return new UUID(makeMsb(getCurrentTimestamp()), CLOCK_SEQ_AND_NODE.get()).toString().replaceAll("-", "");
    }

    /**
     * Returns the Unix timestamp contained by the provided time-based UUID.
     *
     * <p>This method is not equivalent to {@link UUID#timestamp()}. More precisely, a version 1 UUID
     * stores a timestamp that represents the number of 100-nanoseconds intervals since midnight, 15
     * October 1582 and that is what {@link UUID#timestamp()} returns. This method however converts
     * that timestamp to the equivalent Unix timestamp in milliseconds, i.e. a timestamp representing
     * a number of milliseconds since midnight, January 1, 1970 UTC. In particular, the timestamps
     * returned by this method are comparable to the timestamps returned by {@link
     * System#currentTimeMillis}, {@link Date#getTime}, etc.
     *
     * @throws IllegalArgumentException if {@code uuid} is not a version 1 UUID.
     */
    public static long unixTimestamp(String uuidStr) {
        var uuid = fromString(uuidStr);
        if (uuid.version() != 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "Can only retrieve the unix timestamp for version 1 uuid (provided version %d)",
                            uuid.version()));
        }
        long timestamp = uuid.timestamp();
        return (timestamp / 10000) + START_EPOCH_MILLIS;
    }

    public static UUID fromString(String uuidStr) {
        if (uuidStr.contains("-")) {
            return UUID.fromString(uuidStr);
        }

        var seg1 = uuidStr.substring(0, 8);
        var seg2 = uuidStr.substring(8, 12);
        var seg3 = uuidStr.substring(12, 16);
        var seg4 = uuidStr.substring(16, 20);
        var seg5 = uuidStr.substring(20, 32);
        return UUID.fromString(String.format("%s-%s-%s-%s-%s", seg1, seg2, seg3, seg4, seg5));
    }

    // Use {@link System#currentTimeMillis} for a base time in milliseconds, and if we are in the same
    // millisecond as the previous generation, increment the number of nanoseconds.
    // However, since the precision is 100-nanosecond intervals, we can only generate 10K UUIDs within
    // a millisecond safely. If we detect we have already generated that much UUIDs within a
    // millisecond (which, while admittedly unlikely in a real application, is very achievable on even
    // modest machines), then we stall the generator (busy spin) until the next millisecond as
    // required by the RFC.
    private static long getCurrentTimestamp() {
        while (true) {
            long now = fromUnixTimestamp(System.currentTimeMillis());
            long last = lastTimestamp.get();
            if (now > last) {
                if (lastTimestamp.compareAndSet(last, now)) {
                    return now;
                }
            } else {
                long lastMillis = millisOf(last);
                // If the clock went back in time, bail out
                if (millisOf(now) < millisOf(last)) {
                    return lastTimestamp.incrementAndGet();
                }
                long candidate = last + 1;
                // If we've generated more than 10k uuid in that millisecond, restart the whole process
                // until we get to the next millis. Otherwise, we try use our candidate ... unless we've
                // been beaten by another thread in which case we try again.
                if (millisOf(candidate) == lastMillis && lastTimestamp.compareAndSet(last, candidate)) {
                    return candidate;
                }
            }
        }
    }

    private static long fromUnixTimestamp(long tstamp) {
        return (tstamp - START_EPOCH_MILLIS) * 10000;
    }

    private static long millisOf(long timestamp) {
        return timestamp / 10000;
    }

    // Lazily initialize clock seq + node value at time of first access.  Quarkus will attempt to
    // initialize this class at deployment time which prevents us from just setting this value
    // directly.  The "node" part of the clock seq + node includes the current PID which (for
    // GraalVM users) we obtain via the LLVM interop.  That infrastructure isn't setup at Quarkus
    // deployment time, however, thus we can't just call makeClockSeqAndNode() in an initializer.
    // See JAVA-2663 for more detail on this point.
    //
    // Container impl adapted from Guava's memoized Supplier impl.
    private static class ClockSeqAndNodeContainer {

        private volatile boolean initialized = false;
        private long val;

        private long get() {
            if (!initialized) {
                synchronized (ClockSeqAndNodeContainer.class) {
                    if (!initialized) {

                        initialized = true;
                        val = makeClockSeqAndNode();
                    }
                }
            }
            return val;
        }
    }

    private static long makeClockSeqAndNode() {
        long clock = new Random(System.currentTimeMillis()).nextLong();
        long node = makeNode();

        long lsb = 0;
        lsb |= (clock & 0x0000000000003FFFL) << 48;
        lsb |= 0x8000000000000000L;
        lsb |= node;
        return lsb;
    }

    private static long makeNode() {

        // We don't have access to the MAC address (in pure JAVA at least) but need to generate a node
        // part that identifies this host as uniquely as possible.
        // The spec says that one option is to take as many sources that identify this node as possible
        // and hash them together. That's what we do here by gathering all the IPs of this host as well
        // as a few other sources.
        try {

            MessageDigest digest = MessageDigest.getInstance("MD5");
            for (String address : getAllLocalAddresses()) update(digest, address);

            Properties props = System.getProperties();
            update(digest, props.getProperty("java.vendor"));
            update(digest, props.getProperty("java.vendor.url"));
            update(digest, props.getProperty("java.version"));
            update(digest, props.getProperty("os.arch"));
            update(digest, props.getProperty("os.name"));
            update(digest, props.getProperty("os.version"));

            byte[] hash = digest.digest();

            long node = 0;
            for (int i = 0; i < 6; i++) node |= (0x00000000000000ffL & (long) hash[i]) << (i * 8);
            // Since we don't use the MAC address, the spec says that the multicast bit (least significant
            // bit of the first byte of the node ID) must be 1.
            return node | 0x0000010000000000L;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static long makeMsb(long timestamp) {
        long msb = 0L;
        msb |= (0x00000000ffffffffL & timestamp) << 32;
        msb |= (0x0000ffff00000000L & timestamp) >>> 16;
        msb |= (0x0fff000000000000L & timestamp) >>> 48;
        msb |= 0x0000000000001000L; // sets the version to 1.
        return msb;
    }

    private static void update(MessageDigest digest, String value) {
        if (value != null) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Set<String> getAllLocalAddresses() {
        Set<String> allIps = new HashSet<>();
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            allIps.add(localhost.toString());
            // Also return the hostname if available, it won't hurt (this does a dns lookup, it's only
            // done once at startup)
            allIps.add(localhost.getCanonicalHostName());
            InetAddress[] allMyIps = InetAddress.getAllByName(localhost.getCanonicalHostName());
            if (allMyIps != null) {
                for (InetAddress allMyIp : allMyIps) {
                    allIps.add(allMyIp.toString());
                }
            }
        } catch (UnknownHostException e) {
            // Ignore, we'll try the network interfaces anyway
        }

        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            if (en != null) {
                while (en.hasMoreElements()) {
                    Enumeration<InetAddress> enumIpAddr = en.nextElement().getInetAddresses();
                    while (enumIpAddr.hasMoreElements()) {
                        allIps.add(enumIpAddr.nextElement().toString());
                    }
                }
            }
        } catch (SocketException e) {
            // Ignore, if we've really got nothing so far, we'll throw an exception
        }
        return allIps;
    }
}
