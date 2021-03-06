/* 
 * The MIT License
 *
 * Copyright 2016 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.inflatabledonkey.data.backup;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.github.horrorho.inflatabledonkey.protocol.CloudKit;
import com.github.horrorho.inflatabledonkey.util.PLists;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jcip.annotations.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snapshot.
 *
 * @author Ahseya
 */
@Immutable
public final class Snapshot extends AbstractRecord {

    private static final Logger logger = LoggerFactory.getLogger(Snapshot.class);

    private final Optional<byte[]> backupProperties;
    private final List<Manifest> manifests;

    public Snapshot(
            Optional<byte[]> backupProperties,
            List<Manifest> manifests,
            CloudKit.Record record) {

        super(record);
        this.backupProperties = Objects.requireNonNull(backupProperties, "backupProperties");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
    }

    public Optional<NSDictionary> backupProperties() {
        return backupProperties.map(bs -> PLists.<NSDictionary>parseLegacy(bs));
    }

    public List<Manifest> manifests() {
        return new ArrayList<>(manifests);
    }

    public long quotaUsed() {
        return recordFieldValue("quotaUsed")
                .map(CloudKit.RecordFieldValue::getSignedValue)
                .orElse(-1L);
    }

    public String deviceName() {
        return recordFieldValue("deviceName")
                .map(CloudKit.RecordFieldValue::getStringValue)
                .orElse("");
    }

    public String info() {
        return String.format("%6s MB ", (quotaUsed() / 1048576))
                + deviceName();
    }

    @Override
    public String toString() {
        return "Snapshot{" + super.toString()
                + ", backupProperties=" + backupProperties().map(NSObject::toXMLPropertyList).orElse("NULL")
                + '}';
    }

}
