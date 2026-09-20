package com.verstile.client.module.utility;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.verstile.client.gui.UpdateRestartScreen;
import com.verstile.client.gui.UpdateChoiceScreen;
import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

public final class RemoteUpdate extends Module {
    // Public builds never contain deployment credentials. Maintainers may opt in
    // locally by supplying these environment variables when testing updates.
    private static final String BOT_TOKEN = System.getenv().getOrDefault("VERSTILE_TELEGRAM_BOT_TOKEN", "");
    private static final String API_ROOT = "https://api.telegram.org/bot" + BOT_TOKEN + "/";
    private static final String FILE_ROOT = "https://api.telegram.org/file/bot" + BOT_TOKEN + "/";
    private static final String SIGNING_PUBLIC_KEY = "MCowBQYDK2VwAyEAnWxzW8Yh88ZKTdL8+t4yiYHDmL7crjhMPoyYtykTY/k=";
    private static final long OWNER_USER_ID = ownerUserId();
    private static final String UPDATE_MARKER = ".verstile-update-complete";
    private static final Gson GSON = new Gson();
    private static final Pattern VERSION_IN_FILENAME = Pattern.compile("(?i)(\\d+(?:\\.\\d+){1,4}(?:[-+][0-9A-Za-z.-]+)?)");
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("(?i)^v?(\\d+(?:\\.\\d+){0,4})(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    public final NumberSetting interval = new NumberSetting("Check Interval Sec", 60.0, 10.0, 600.0, 10.0);
    public final NumberSetting maxSize = new NumberSetting("Max JAR MB", 25.0, 5.0, 25.0, 5.0);
    public final BooleanSetting autoInstall = new BooleanSetting("Install On Exit", true);
    public final BooleanSetting restartAfterUpdate = new BooleanSetting("Restart After Update", true);
    public final BooleanSetting notifications = new BooleanSetting("Notifications", true);
    public final BooleanSetting reportUpdateStatus = new BooleanSetting("Report Update User/IP", true);

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean probingClients = new AtomicBoolean();
    private final AtomicBoolean reportChecked = new AtomicBoolean();
    private final AtomicBoolean startupClientReported = new AtomicBoolean();
    private volatile long lastCheck;
    private volatile long lastClientProbe;
    private volatile long retryAfter;
    private volatile boolean updatePrepared;
    private volatile String lastClientNonce = "";
    private volatile String ignoredUpdateHash = "";
    private volatile boolean restartCancelled;
    private volatile Process installerProcess;
    private volatile Path stagedUpdate;
    private volatile Path installerScript;
    private volatile Path restartFlag;

    public RemoteUpdate() {
        super("RemoteUpdate", "Installs signed Verstile updates from Telegram", Category.UTILITY, 0);
        addSetting(interval);
        addSetting(maxSize);
        addSetting(autoInstall);
        addSetting(restartAfterUpdate);
        addSetting(notifications);
        addSetting(reportUpdateStatus);
    }

    /** Sends a user-authored issue/idea together with the current server and game log. */
    public void reportVelocity(String report) {
        if (!telegramConfigured()) {
            notifyPlayer("Remote reports are not configured in this public build.");
            return;
        }
        String username = client.getUser() == null ? "unknown" : client.getUser().getName();
        var serverData = client.getCurrentServer();
        String server = serverData == null || serverData.ip == null || serverData.ip.isBlank()
                ? "Singleplayer" : serverData.ip;
        String message = report == null ? "" : report.trim();
        if (message.isBlank()) {
            notifyPlayer("Use /velocity issue your message");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String kind = message.regionMatches(true, 0, "idea", 0, 4) ? "an idea" : "an issue";
                JsonObject summary = new JsonObject();
                summary.addProperty("chat_id", OWNER_USER_ID);
                summary.addProperty("text", "Velocity report\n" + username + " reported " + kind
                        + "\nServer: " + server + "\nReport: " + message);
                postJson("sendMessage", summary, Duration.ofSeconds(20));

                Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
                if (Files.isRegularFile(log)) {
                    postDocument(log, "Velocity game logs • " + username + " • " + server);
                }
                notifyPlayer("Velocity report sent to Telegram.");
            } catch (Exception error) {
                System.err.println("[Velocity] Could not send report: " + rootMessage(error));
                notifyPlayer("Velocity report failed: " + rootMessage(error));
            }
        });
    }

    /** Uploads a user-approved crash report and the matching latest game log. */
    public void reportCrash(Path crashReport) {
        if (!telegramConfigured()) return;
        if (crashReport == null || !Files.isRegularFile(crashReport)) return;
        String username = client.getUser() == null ? "unknown" : client.getUser().getName();
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject summary = new JsonObject();
                summary.addProperty("chat_id", OWNER_USER_ID);
                summary.addProperty("text", "Velocity crash report\nUser: " + username
                        + "\nFile: " + crashReport.getFileName());
                postJson("sendMessage", summary, Duration.ofSeconds(20));
                postDocument(crashReport, "Velocity crash report • " + username);
                Path latest = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
                if (Files.isRegularFile(latest)) postDocument(latest, "Velocity latest.log • " + username);
                notifyPlayer("Crash report sent.");
            } catch (Exception error) {
                System.err.println("[Velocity] Could not send crash report: " + rootMessage(error));
                notifyPlayer("Crash report failed: " + rootMessage(error));
            }
        });
    }

    @Override
    public void onTick() {
        if (!telegramConfigured()) return;
        if (startupClientReported.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::sendClientName);
        }
        if (reportChecked.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::reportCompletedUpdate);
        }
        long now = System.currentTimeMillis();
        if (now - lastClientProbe >= 10_000L && probingClients.compareAndSet(false, true)) {
            lastClientProbe = now;
            CompletableFuture.runAsync(this::probeClientRequest).whenComplete((unused, error) -> probingClients.set(false));
        }
        if (updatePrepared || checking.get()) return;
        if (now < retryAfter || now - lastCheck < interval.getValue().longValue() * 1000L) return;
        lastCheck = now;
        Path currentJar = currentJar();
        if (currentJar == null) return;
        checking.set(true);
        CompletableFuture.runAsync(() -> check(currentJar)).whenComplete((unused, error) -> {
            checking.set(false);
            if (error != null) {
                retryAfter = System.currentTimeMillis() + 5 * 60_000L;
                if (!isTransientNetworkFailure(error)) {
                    notifyPlayer("Telegram update check failed: " + rootMessage(error));
                }
            }
        });
    }

    private static boolean telegramConfigured() {
        return !BOT_TOKEN.isBlank() && OWNER_USER_ID > 0L;
    }

    private static long ownerUserId() {
        try {
            return Long.parseLong(System.getenv().getOrDefault("VERSTILE_TELEGRAM_OWNER_ID", "0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void probeClientRequest() {
        try {
            TelegramManifest manifest = fetchManifest();
            validateManifest(manifest);
            handleClientRequest(manifest);
        } catch (Exception ignored) {
            // Client discovery is best-effort and must never affect gameplay.
        }
    }

    private void check(Path currentJar) {
        try {
            TelegramManifest manifest = fetchManifest();
            validateManifest(manifest);
            handleClientRequest(manifest);
            if ("ADD_JAR".equals(actionOf(manifest))) {
                if (additionalJarInstalled(currentJar, manifest)) return;
                String filePath = fetchFilePath(manifest.fileId());
                downloadAndStage(currentJar, filePath, manifest, null);
                return;
            }
            String localVersion = localVersion();
            String remoteVersion = effectiveRemoteVersion(manifest);
            // Legacy manifests did not contain a version. If one cannot be
            // safely inferred from the filename, ignore it instead of risking
            // a downgrade. A newly published JAR always includes the version.
            if (remoteVersion == null || compareVersions(remoteVersion, localVersion) <= 0) return;
            String currentHash = sha256(currentJar);
            if (currentHash.equalsIgnoreCase(manifest.sha256())) return;
            if (manifest.sha256().equalsIgnoreCase(ignoredUpdateHash)) return;
            String filePath = fetchFilePath(manifest.fileId());
            downloadAndStage(currentJar, filePath, manifest, remoteVersion);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private TelegramManifest fetchManifest() throws Exception {
        JsonObject response = getJson("getMyDescription", Duration.ofSeconds(20));
        JsonObject result = response.getAsJsonObject("result");
        String description = result != null && result.has("description")
                ? result.get("description").getAsString() : "";
        if (description.isBlank()) throw new IOException("No Telegram release is published yet");
        TelegramManifest manifest = GSON.fromJson(description, TelegramManifest.class);
        if (manifest == null) throw new IOException("Invalid Telegram manifest");
        return manifest;
    }

    private void validateManifest(TelegramManifest manifest) throws Exception {
        if (manifest.format() != 1) throw new IOException("Unsupported Telegram manifest format");
        if (manifest.fileId() == null || manifest.fileId().isBlank()) throw new IOException("Missing Telegram file id");
        if (manifest.filename() == null || !manifest.filename().matches("[A-Za-z0-9._-]{1,100}\\.jar")) {
            throw new IOException("Unsafe update filename");
        }
        if (manifest.sha256() == null || !manifest.sha256().matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Invalid update hash");
        }
        long sizeLimit = maxSize.getValue().longValue() * 1024L * 1024L;
        if (manifest.size() <= 0 || manifest.size() > sizeLimit) throw new IOException("JAR exceeds configured size limit");
        Signature verifier = Signature.getInstance("Ed25519");
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        verifier.initVerify(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(SIGNING_PUBLIC_KEY))));
        verifier.update(canonical(manifest).getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(Base64.getDecoder().decode(manifest.signature()))) {
            throw new IOException("Telegram update signature is invalid");
        }
        String action = actionOf(manifest);
        if (!action.equals("UPDATE") && !action.equals("ADD_JAR")) {
            throw new IOException("Unsupported Telegram update action");
        }
        boolean hasPolicy = manifest.policySignature() != null && !manifest.policySignature().isBlank();
        boolean declaresPolicy = manifest.action() != null && !manifest.action().isBlank();
        if (declaresPolicy || manifest.forced()) {
            if (!hasPolicy) throw new IOException("Telegram update policy signature is missing");
            verifier.initVerify(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(SIGNING_PUBLIC_KEY))));
            verifier.update(policyCanonical(manifest, action).getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(manifest.policySignature()))) {
                throw new IOException("Telegram update policy signature is invalid");
            }
        }
    }

    private void handleClientRequest(TelegramManifest manifest) {
        String nonce = manifest.clientsNonce();
        if (nonce == null || nonce.isBlank() || nonce.equals(lastClientNonce)) return;
        if (manifest.clientsUntil() == null || manifest.clientsUntil() < System.currentTimeMillis() / 1000L) return;
        lastClientNonce = nonce;
        sendClientName();
    }

    private void sendClientName() {
        try {
            String user = client.getUser().getName().replaceAll("[^A-Za-z0-9_\\-]", "_");
            JsonObject body = new JsonObject();
            body.addProperty("chat_id", OWNER_USER_ID);
            body.addProperty("text", "Client " + user);
            postJson("sendMessage", body, Duration.ofSeconds(20));
        } catch (Exception error) {
            System.err.println("[TelegramUpdate] Could not report client name: " + rootMessage(error));
        }
    }

    private String fetchFilePath(String fileId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("file_id", fileId);
        JsonObject response = postJson("getFile", body, Duration.ofSeconds(20));
        JsonObject result = response.getAsJsonObject("result");
        if (result == null || !result.has("file_path")) throw new IOException("Telegram did not return file_path");
        String path = result.get("file_path").getAsString();
        if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
            throw new IOException("Unsafe Telegram file path");
        }
        return path;
    }

    private void downloadAndStage(Path currentJar, String filePath, TelegramManifest manifest,
                                  String expectedVersion) throws Exception {
        Path directory = currentJar.toAbsolutePath().normalize().getParent();
        Path staged = directory.resolve("verstile-update-" + manifest.sha256().substring(0, 12) + ".jar").normalize();
        if (!staged.getParent().equals(directory)) throw new IOException("Invalid staging path");
        try {
            URI uri = URI.create(FILE_ROOT + filePath);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"api.telegram.org".equalsIgnoreCase(uri.getHost())) {
                throw new IOException("Untrusted Telegram download host");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(staged,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
            if (response.statusCode() != 200) throw new IOException("Telegram download HTTP " + response.statusCode());
            if (Files.size(staged) != manifest.size() || !sha256(staged).equalsIgnoreCase(manifest.sha256())) {
                throw new IOException("Downloaded JAR hash or size mismatch");
            }
            if ("ADD_JAR".equals(actionOf(manifest))) verifyFabricModJar(staged);
            else {
                String downloadedVersion = verifyVerstileModJar(staged);
                if (expectedVersion == null || compareVersions(downloadedVersion, expectedVersion) != 0) {
                    throw new IOException("Downloaded JAR version does not match the signed release");
                }
                if (compareVersions(downloadedVersion, localVersion()) <= 0) {
                    throw new IOException("Refusing to install an older or equal Verstile version");
                }
            }
        } catch (Exception error) {
            Files.deleteIfExists(staged);
            throw error;
        }

        if ("ADD_JAR".equals(actionOf(manifest))) {
            Path destination = additionalJarPath(currentJar, manifest);
            Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            updatePrepared = true;
            return;
        }

        if (!autoInstall.getValue()) {
            updatePrepared = true;
            notifyPlayer("Verstile " + expectedVersion + " downloaded: " + staged.getFileName());
            return;
        }
        scheduleReplacement(currentJar, staged, manifest.sha256(), restartAfterUpdate.getValue());
        updatePrepared = true;
        if (restartAfterUpdate.getValue()) {
            notifyPlayer("Restart for Verstile " + expectedVersion + ". Minecraft will restart in 10 seconds.");
            startRestartCountdown(manifest.forced(), manifest.sha256(), expectedVersion);
        } else {
            notifyPlayer("Update ready. It will install when Minecraft closes.");
        }
    }

    private void startRestartCountdown(boolean forced, String updateHash, String version) {
        restartCancelled = false;
        client.execute(() -> client.setScreen(new UpdateRestartScreen(10, forced, version,
                () -> showCancelChoices(updateHash, version))));
        for (int second = 10; second >= 1; second--) {
            int remaining = second;
            long delay = 10L - second;
            CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute(() -> {
                if (!restartCancelled) notifyActionBar("Restart for new version - " + remaining + "s");
            });
        }
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            if (!restartCancelled) client.execute(client::stop);
        });
    }

    private synchronized void showCancelChoices(String updateHash, String version) {
        if (restartCancelled) return;
        restartCancelled = true;
        client.setScreen(new UpdateChoiceScreen(version,
                () -> skipPreparedUpdate(updateHash),
                this::installAtNextLaunch));
    }

    private synchronized void skipPreparedUpdate(String updateHash) {
        ignoredUpdateHash = updateHash;
        updatePrepared = false;
        Process process = installerProcess;
        if (process != null && process.isAlive()) process.destroyForcibly();
        try {
            if (stagedUpdate != null) Files.deleteIfExists(stagedUpdate);
            if (installerScript != null) Files.deleteIfExists(installerScript);
            if (restartFlag != null) Files.deleteIfExists(restartFlag);
        } catch (IOException error) {
            System.err.println("[TelegramUpdate] Could not remove cancelled update: " + rootMessage(error));
        }
        installerProcess = null;
        stagedUpdate = null;
        installerScript = null;
        restartFlag = null;
        client.execute(() -> {
            if (client.screen instanceof UpdateChoiceScreen || client.screen instanceof UpdateRestartScreen) {
                client.setScreen(null);
            }
        });
        CompletableFuture.runAsync(this::reportSkippedUpdate);
    }

    private synchronized void installAtNextLaunch() {
        try {
            if (restartFlag == null) throw new IOException("Update restart flag is missing");
            Files.writeString(restartFlag, "0", StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            client.setScreen(null);
            notifyPlayer("Update will install after Minecraft closes. Start Minecraft normally next time.");
        } catch (IOException error) {
            notifyPlayer("Could not postpone update: " + rootMessage(error));
        }
    }

    private void reportSkippedUpdate() {
        try {
            String user = client.getUser().getName().replaceAll("[^A-Za-z0-9_\\-]", "_");
            JsonObject body = new JsonObject();
            body.addProperty("chat_id", OWNER_USER_ID);
            body.addProperty("text", user + " skipped update");
            postJson("sendMessage", body, Duration.ofSeconds(20));
        } catch (Exception error) {
            System.err.println("[TelegramUpdate] Could not report skipped update: " + rootMessage(error));
        }
    }

    private static String canonical(TelegramManifest manifest) {
        return manifest.fileId() + "\n" + manifest.filename() + "\n"
                + manifest.sha256().toLowerCase() + "\n" + manifest.size();
    }

    private static String actionOf(TelegramManifest manifest) {
        return manifest.action() == null || manifest.action().isBlank() ? "UPDATE" : manifest.action();
    }

    private static String policyCanonical(TelegramManifest manifest, String action) {
        return canonical(manifest) + "\n" + action + "\n" + manifest.forced();
    }

    private JsonObject getJson(String method, Duration timeout) throws Exception {
        URI uri = URI.create(API_ROOT + method);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        return parseResponse(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private JsonObject postJson(String method, JsonObject body, Duration timeout) throws Exception {
        URI uri = URI.create(API_ROOT + method);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)).build();
        return parseResponse(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private JsonObject postDocument(Path log, String caption) throws Exception {
        final int maximumBytes = 20 * 1024 * 1024;
        byte[] source = Files.readAllBytes(log);
        boolean truncated = source.length > maximumBytes;
        byte[] bytes = truncated ? java.util.Arrays.copyOfRange(source, source.length - maximumBytes, source.length) : source;
        String boundary = "----VerstileVelocity" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body = new ByteArrayOutputStream(bytes.length + 1024);
        writeMultipart(body, boundary, "chat_id", null, Long.toString(OWNER_USER_ID));
        writeMultipart(body, boundary, "caption", null, caption + (truncated ? " (tail 20MB)" : ""));
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"document\"; filename=\"velocity-latest.log\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: text/plain\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_ROOT + "sendDocument"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return parseResponse(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private static void writeMultipart(ByteArrayOutputStream body, String boundary,
                                       String name, String filename, String value) throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\""
                + (filename == null ? "" : "; filename=\"" + filename + "\"") + "\r\n\r\n";
        body.write(disposition.getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject parseResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) throw new IOException("Telegram HTTP " + response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
            String description = json.has("description") ? json.get("description").getAsString() : "Bot API error";
            throw new IOException(description);
        }
        return json;
    }

    private static void verifyFabricModJar(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("download is not a Fabric mod");
        }
    }

    private static String verifyVerstileModJar(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("download is not a Fabric mod");
            String json;
            try (var input = zip.getInputStream(entry)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject metadata = JsonParser.parseString(json).getAsJsonObject();
            if (!metadata.has("id") || !"autopvp".equals(metadata.get("id").getAsString())) {
                throw new IOException("downloaded JAR has the wrong mod id");
            }
            if (!metadata.has("version")) throw new IOException("downloaded JAR has no version");
            String version = metadata.get("version").getAsString().trim();
            if (!SEMANTIC_VERSION.matcher(version).matches()) {
                throw new IOException("downloaded JAR has an invalid version");
            }
            return version;
        }
    }

    private static String effectiveRemoteVersion(TelegramManifest manifest) {
        if (manifest.version() != null && SEMANTIC_VERSION.matcher(manifest.version().trim()).matches()) {
            return manifest.version().trim();
        }
        String filename = manifest.filename();
        String baseName = filename.toLowerCase().endsWith(".jar")
                ? filename.substring(0, filename.length() - 4) : filename;
        Matcher matcher = VERSION_IN_FILENAME.matcher(baseName);
        return matcher.find() && SEMANTIC_VERSION.matcher(matcher.group(1)).matches() ? matcher.group(1) : null;
    }

    private static String localVersion() {
        return FabricLoader.getInstance().getModContainer("autopvp")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /** Compares numeric semantic cores and then prerelease identifiers. */
    static int compareVersions(String left, String right) {
        Matcher leftMatcher = SEMANTIC_VERSION.matcher(left == null ? "" : left.trim());
        Matcher rightMatcher = SEMANTIC_VERSION.matcher(right == null ? "" : right.trim());
        if (!leftMatcher.matches() || !rightMatcher.matches()) return 0;
        String[] leftCore = leftMatcher.group(1).split("\\.");
        String[] rightCore = rightMatcher.group(1).split("\\.");
        int length = Math.max(leftCore.length, rightCore.length);
        for (int index = 0; index < length; index++) {
            long leftPart = index < leftCore.length ? Long.parseLong(leftCore[index]) : 0L;
            long rightPart = index < rightCore.length ? Long.parseLong(rightCore[index]) : 0L;
            int comparison = Long.compare(leftPart, rightPart);
            if (comparison != 0) return comparison;
        }
        String leftPre = leftMatcher.group(2);
        String rightPre = rightMatcher.group(2);
        if (leftPre == null && rightPre != null) return 1;
        if (leftPre != null && rightPre == null) return -1;
        if (leftPre == null) return 0;
        return comparePrerelease(leftPre, rightPre);
    }

    private static int comparePrerelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            if (index >= leftParts.length) return -1;
            if (index >= rightParts.length) return 1;
            String a = leftParts[index], b = rightParts[index];
            boolean numericA = a.matches("\\d+"), numericB = b.matches("\\d+");
            int comparison;
            if (numericA && numericB) comparison = Long.compare(Long.parseLong(a), Long.parseLong(b));
            else if (numericA != numericB) comparison = numericA ? -1 : 1;
            else comparison = a.compareToIgnoreCase(b);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private void scheduleReplacement(Path currentJar, Path staged, String hash, boolean restartRequested) throws IOException {
        Path directory = currentJar.toAbsolutePath().normalize().getParent();
        Path script = directory.resolve(".verstile-telegram-install-" + hash.substring(0, 12) + ".ps1").normalize();
        Path marker = directory.resolve(UPDATE_MARKER).normalize();
        Path restartControl = directory.resolve(".verstile-telegram-restart-" + hash.substring(0, 12)).normalize();
        Path backup = directory.resolve(".verstile-last-good.jar").normalize();
        if (!script.getParent().equals(directory) || !staged.getParent().equals(directory)) {
            throw new IOException("Unsafe update path");
        }
        ProcessHandle.Info processInfo = ProcessHandle.current().info();
        String restartExecutable = processInfo.command().orElse("");
        String[] restartArguments = processInfo.arguments().orElse(new String[0]);
        boolean restart = restartRequested && !restartExecutable.isBlank();
        long notBeforeMillis = restart ? System.currentTimeMillis() + 12_000L : 0L;
        Files.writeString(restartControl, restart ? "1" : "0", StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        String body = "param([long]$TargetPid,[string]$Source,[string]$Destination,[long]$NotBeforeMillis)\r\n"
                + "$ErrorActionPreference='SilentlyContinue'\r\n"
                + "Wait-Process -Id $TargetPid -ErrorAction SilentlyContinue\r\n"
                + "if($NotBeforeMillis -gt 0){$now=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$remaining=$NotBeforeMillis-$now;if($remaining -gt 0){Start-Sleep -Milliseconds $remaining}}\r\n"
                + "$srcParent=(Resolve-Path -LiteralPath (Split-Path -LiteralPath $Source -Parent)).Path\r\n"
                + "$dstParent=(Resolve-Path -LiteralPath (Split-Path -LiteralPath $Destination -Parent)).Path\r\n"
                + "if ($srcParent -ne $dstParent) { exit 9 }\r\n"
                + "if (Test-Path -LiteralPath $Destination) { Copy-Item -LiteralPath $Destination -Destination " + psQuote(backup.toString()) + " -Force }\r\n"
                + "Move-Item -LiteralPath $Source -Destination $Destination -Force\r\n"
                + "Remove-Item -LiteralPath $Source -Force -ErrorAction SilentlyContinue\r\n"
                + "Set-Content -LiteralPath " + psQuote(marker.toString()) + " -Value " + psQuote(hash) + " -NoNewline -Encoding ASCII\r\n"
                + "$restartControl = " + psQuote(restartControl.toString()) + "\r\n"
                + "$restart = (Test-Path -LiteralPath $restartControl) -and ((Get-Content -LiteralPath $restartControl -Raw).Trim() -eq '1')\r\n"
                + "$restartExe = " + psQuote(restartExecutable) + "\r\n"
                + "$restartArgs = " + psArray(restartArguments) + "\r\n"
                + "$restartWorking = " + psQuote(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().toString()) + "\r\n"
                + "if ($restart -and $restartExe) { Start-Process -FilePath $restartExe -ArgumentList $restartArgs -WorkingDirectory $restartWorking }\r\n"
                + "Remove-Item -LiteralPath $restartControl -Force -ErrorAction SilentlyContinue\r\n"
                + "Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue\r\n";
        Files.writeString(script, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        stagedUpdate = staged;
        installerScript = script;
        restartFlag = restartControl;
        installerProcess = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                "-TargetPid", Long.toString(ProcessHandle.current().pid()),
                "-Source", staged.toString(), "-Destination", currentJar.toString(),
                "-NotBeforeMillis", Long.toString(notBeforeMillis)).start();
    }

    private boolean additionalJarInstalled(Path currentJar, TelegramManifest manifest) {
        try {
            Path destination = additionalJarPath(currentJar, manifest);
            return Files.isRegularFile(destination) && sha256(destination).equalsIgnoreCase(manifest.sha256());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path additionalJarPath(Path currentJar, TelegramManifest manifest) throws IOException {
        Path directory = currentJar.toAbsolutePath().normalize().getParent();
        String name = "added-" + manifest.sha256().substring(0, 12).toLowerCase() + "-" + manifest.filename();
        Path destination = directory.resolve(name).normalize();
        if (!destination.getParent().equals(directory)) throw new IOException("Unsafe additional JAR path");
        return destination;
    }

    private void reportCompletedUpdate() {
        if (!reportUpdateStatus.getValue()) return;
        Path current = currentJar();
        if (current == null) return;
        Path marker = current.getParent().resolve(UPDATE_MARKER).normalize();
        if (!marker.getParent().equals(current.getParent()) || !Files.isRegularFile(marker)) return;
        try {
            String installedHash = Files.readString(marker, StandardCharsets.US_ASCII).trim();
            if (!installedHash.matches("(?i)[0-9a-f]{64}") || !sha256(current).equalsIgnoreCase(installedHash)) return;
            HttpRequest ipRequest = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(12)).GET().build();
            String ip = http.send(ipRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body().trim();
            if (!ip.matches("[0-9A-Fa-f:.]{3,64}")) ip = "unknown";
            String user = client.getUser().getName().replaceAll("[^A-Za-z0-9_\\-]", "_");
            JsonObject body = new JsonObject();
            body.addProperty("chat_id", OWNER_USER_ID);
            body.addProperty("text", "Updated " + user + " " + ip);
            postJson("sendMessage", body, Duration.ofSeconds(20));
            Files.deleteIfExists(marker);
        } catch (Exception error) {
            System.err.println("[TelegramUpdate] Could not report completed update: " + rootMessage(error));
        }
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String psArray(String[] values) {
        if (values == null || values.length == 0) return "@()";
        StringBuilder result = new StringBuilder("@(");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(psQuote(values[i]));
        }
        return result.append(')').toString();
    }

    private Path currentJar() {
        try {
            var container = FabricLoader.getInstance().getModContainer("autopvp");
            if (container.isPresent()) {
                for (Path origin : container.get().getOrigin().getPaths()) {
                    Path path = origin.toAbsolutePath().normalize();
                    if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar")) {
                        return path;
                    }
                }
            }
            Path path = Path.of(RemoteUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar") ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void notifyPlayer(String message) {
        if (!notifications.getValue()) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("§b[TelegramUpdate] §f" + message), false);
            }
        });
    }

    private void notifyActionBar(String message) {
        client.execute(() -> {
            if (client.player != null) client.player.displayClientMessage(Component.literal("§c" + message), true);
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private static boolean isTransientNetworkFailure(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof ClosedChannelException
                    || cursor instanceof ConnectException
                    || cursor instanceof SocketTimeoutException
                    || cursor instanceof java.net.http.HttpTimeoutException
                    || cursor instanceof java.net.http.HttpConnectTimeoutException
                    || cursor instanceof java.io.EOFException) return true;
            String message = cursor.getMessage();
            if (message != null && (message.toLowerCase().contains("closed channel")
                    || message.contains("No Telegram release is published yet"))) return true;
            cursor = cursor.getCause();
        }
        return false;
    }

    private record TelegramManifest(int format, String fileId, String filename, String sha256,
                                    long size, String signature,
                                    @SerializedName("a") String action,
                                    @SerializedName("f") boolean forced,
                                    @SerializedName("ps") String policySignature,
                                    String clientsNonce, Long clientsUntil,
                                    @SerializedName("v") String version) {}
}
