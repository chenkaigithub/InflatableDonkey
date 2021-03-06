/* 
 * The MIT License
 *
 * Copyright 2015 Ahseya.
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
package com.github.horrorho.inflatabledonkey;

import com.github.horrorho.inflatabledonkey.args.Property;
import com.github.horrorho.inflatabledonkey.args.PropertyLoader;
import com.github.horrorho.inflatabledonkey.chunk.engine.standard.StandardChunkEngine;
import com.github.horrorho.inflatabledonkey.chunk.store.disk.DiskChunkStore;
import com.github.horrorho.inflatabledonkey.cloud.AssetDownloader;
import com.github.horrorho.inflatabledonkey.cloud.AuthorizeAssets;
import com.github.horrorho.inflatabledonkey.cloud.accounts.Account;
import com.github.horrorho.inflatabledonkey.cloud.accounts.Accounts;
import com.github.horrorho.inflatabledonkey.cloud.auth.Auth;
import com.github.horrorho.inflatabledonkey.cloud.auth.Authenticator;
import com.github.horrorho.inflatabledonkey.data.backup.Asset;
import com.github.horrorho.inflatabledonkey.data.backup.Assets;
import com.github.horrorho.inflatabledonkey.data.backup.Device;
import com.github.horrorho.inflatabledonkey.data.backup.Snapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InflatableDonkey.
 *
 * @author Ahseya
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * @param args the command line arguments
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // MUST initialize Property with args before we use it, otherwise it will throw an IllegalStateException.
        try {
            if (!PropertyLoader.instance().test(args)) {
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Argument error: " + ex.getMessage());
            System.out.println("Try '" + Property.APP_NAME.value().orElse("") + " --help' for more information.");
            System.exit(-1);
        }

        // SystemDefault HttpClient.
        // TODO concurrent
        CloseableHttpClient httpClient = HttpClients.custom()
                .setUserAgent("CloudKit/479 (13A404)")
                .setRedirectStrategy(new LaxRedirectStrategy())
                .useSystemProperties()
                .build();

        // Auth
        // TODO rework when we have UncheckedIOException for Authenticator
        Auth auth = Property.AUTHENTICATION_TOKEN.value()
                .map(Auth::new)
                .orElse(null);

        if (auth == null) {
            auth = Authenticator.authenticate(
                    httpClient,
                    Property.AUTHENTICATION_APPLEID.value().get(),
                    Property.AUTHENTICATION_PASSWORD.value().get());
        }
        logger.debug("-- main() - auth: {}", auth);

        if (Property.ARGS_TOKEN.booleanValue().orElse(false)) {
            System.out.println("DsPrsID:mmeAuthToken " + auth.dsPrsID() + ":" + auth.mmeAuthToken());
            return;
        }

        logger.info("-- main() - Apple ID: {}", Property.AUTHENTICATION_APPLEID.value());
        logger.info("-- main() - password: {}", Property.AUTHENTICATION_PASSWORD.value().map(s -> s.replaceAll(".", "*")));
        logger.info("-- main() - token: {}", Property.AUTHENTICATION_TOKEN.value().map(s -> s.replaceAll(".", "*")));

        // Account
        Account account = Accounts.account(httpClient, auth);
        logger.info("-- main() - Account: {}", account.accountInfo().appleId());

        // Backup
        BackupAssistant assistant = BackupAssistant.create(httpClient, account);

        // Output folders.
        Path outputFolder = Paths.get(Property.OUTPUT_FOLDER.value().orElse("backups"))
                .resolve(account.accountInfo().appleId());
        Path assetOutputFolder = outputFolder;
        Path chunkOutputFolder = outputFolder.resolve("cache");
        logger.info("-- main() - output folder backups: {}", assetOutputFolder.toAbsolutePath());
        logger.info("-- main() - output folder chunk cache: {}", chunkOutputFolder.toAbsolutePath());

        // Download tools.
        AuthorizeAssets authorizeAssets = AuthorizeAssets.backupd();
        DiskChunkStore chunkStore = new DiskChunkStore(chunkOutputFolder);
        StandardChunkEngine chunkEngine = new StandardChunkEngine(chunkStore);
        AssetDownloader assetDownloader = new AssetDownloader(chunkEngine);
        KeyBagManager keyBagManager = assistant.newKeyBagManager();

        DownloadAssistant downloadAssistant
                = new DownloadAssistant(authorizeAssets, assetDownloader, keyBagManager, outputFolder);

        Backup backup = new Backup(assistant, downloadAssistant);
        Map<Device, List<Snapshot>> deviceSnapshots = backup.snapshots(httpClient);

        if (deviceSnapshots.isEmpty()) {
            System.out.println("No devices.");
            return;
        }

        // List.
        System.out.println("Devices/ snapshots:\n");
        deviceSnapshots.forEach((device, snapshotList) -> {
            System.out.println(device.info());
            Map<String, Instant> timestamps = device.snapshotTimestampMap();
            for (int i = 0; i < snapshotList.size(); i++) {
                Snapshot snapshot = snapshotList.get(i);
                System.out.println("\t" + i + snapshot.info() + "   " + timestamps.get(snapshot.name()));
            }
            if (snapshotList.isEmpty()) {
                System.out.println("\tNo snapshots.");
            }
        });
        if (deviceSnapshots.isEmpty()) {
            System.out.println("\tNo devices.");
            return;
        }

        // Print snapshots option.
        if (Property.PRINT_SNAPSHOTS.booleanValue().orElse(false)) {
            return;
        }

        // Filters.
        Property.FILTER_DEVICE.list().ifPresent(filter -> logger.info("-- main() - device filter: {}", filter));
        List<String> filterDevices = Property.FILTER_DEVICE.list().orElseGet(() -> Collections.emptyList());
        Predicate<Device> deviceFilter = Filters.deviceFilter(filterDevices);

        Property.FILTER_SNAPSHOT.list().ifPresent(filter -> logger.info("-- main() - snapshot filter: {}", filter));
        List<Integer> filterSnapshots = Property.FILTER_SNAPSHOT.intList().orElseGet(() -> Collections.emptyList());
        UnaryOperator<List<Snapshot>> snapshotFilter = Filters.<Snapshot>listFilter(filterSnapshots);

        Map<Device, List<Snapshot>> filtered = deviceSnapshots
                .entrySet()
                .stream()
                .filter(e -> deviceFilter.test(e.getKey()))
                .map(e -> new SimpleImmutableEntry<>(e.getKey(), snapshotFilter.apply(e.getValue())))
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (filtered.isEmpty()) {
            System.out.println("Nothing selected.");
            return;
        }

        filtered.forEach((device, snapshots) -> {
            logger.info("-- main() - selected device: {}", device.info());
            snapshots.forEach(snapshot -> logger.info("-- main() - selected snapshot: {}", snapshot.info()));
        });

        // Print domain list option.
        if (Property.PRINT_DOMAIN_LIST.booleanValue().orElse(false)) {
            backup.printDomainList(httpClient, deviceSnapshots);
            return;
        }

        Property.FILTER_DOMAIN.list().ifPresent(filter -> logger.info("-- main() - domain filter: {}", filter));
        List<String> filterDomains = Property.FILTER_DOMAIN.list().orElseGet(() -> Collections.emptyList());
        Predicate<Assets> domainFilter = Filters.assetsFilter(filterDomains);

        Property.FILTER_EXTENSION.list().ifPresent(filter -> logger.info("-- main() - extension filter: {}", filter));
        List<String> filterExtensions = Property.FILTER_EXTENSION.list().orElseGet(() -> Collections.emptyList());
        Predicate<Asset> assetFilter = Filters.assetFilter(filterExtensions);

        backup.download(httpClient, deviceSnapshots, domainFilter, assetFilter);
    }
}

// TODO 0xFF System protectionInfo
// TODO file timestamps
// TODO date filtering
// TODO size filtering
// TODO time expired tokens / badly adjusted system clocks.
// TODO handle D in files
// TODO reconstruct empty files/ empty directories
// TODO file timestamp
// TODO filtering
// TODO concurrent downloads
// TODO file asset cache
// TODO 5000 limit?